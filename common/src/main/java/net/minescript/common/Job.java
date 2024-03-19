// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.quoteCommand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Job implements JobControl {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  public final int jobId;
  public final ResourceTracker<Object> objects;
  public final ResourceTracker<BlockPack> blockpacks;
  public final ResourceTracker<BlockPacker> blockpackers;

  private final ScriptConfig.BoundCommand command;
  private final Task task;
  private final Config config;
  private final SystemMessageQueue systemMessageQueue;
  private Thread thread;
  private volatile JobState state = JobState.NOT_STARTED;
  private Consumer<Integer> doneCallback;
  private Queue<Message> jobTickQueue = new ConcurrentLinkedQueue<Message>();
  private Queue<Message> jobRenderQueue = new ConcurrentLinkedQueue<Message>();
  private Lock lock = new ReentrantLock(true); // true indicates a fair lock to avoid starvation

  // Key is unique within this job, typically a func call ID.
  private Map<Long, Operation> operations = new ConcurrentHashMap<>();

  // Special prefix for commands and function calls emitted from stdout of scripts, for example:
  // - script function call: "?mnsc:123 X my_func [4, 5, 6]"
  // - script system call: "?mnsc:0 X exit! []"
  private static final String FUNCTION_PREFIX = "?mnsc:";

  public interface Operation {
    String name();

    void suspend();

    boolean resumeAndCheckDone();

    void cancel();
  }

  public Job(
      int jobId,
      ScriptConfig.BoundCommand command,
      Task task,
      Config config,
      SystemMessageQueue systemMessageQueue,
      Consumer<Integer> doneCallback) {
    this.jobId = jobId;
    this.command = command;
    this.task = task;
    this.config = config;
    this.systemMessageQueue = systemMessageQueue;
    this.doneCallback = doneCallback;
    objects = new ResourceTracker<>(Object.class, jobId, config);
    blockpacks = new ResourceTracker<>(BlockPack.class, jobId, config);
    blockpackers = new ResourceTracker<>(BlockPacker.class, jobId, config);
  }

  public ScriptConfig.BoundCommand boundCommand() {
    return command;
  }

  public void addOperation(long opId, Operation op) {
    if (operations.put(opId, op) != null) {
      throw new IllegalStateException("Job added operation with duplicate ID: " + opId);
    }
  }

  public boolean removeOperation(long opId) {
    return operations.remove(opId) == null;
  }

  /**
   * Attempts to cancel an operation associated with opId, returning true if found.
   *
   * <p>If operation is found, it is cancelled and removed from this job.
   */
  public boolean cancelOperation(long opId) {
    if (config.debugOutput()) {
      LOGGER.info("Cancelling operation {} among {} in job {}", opId, operations.keySet(), jobId);
    }

    var op = operations.remove(opId);
    if (op != null) {
      op.cancel();
      return true;
    }

    LOGGER.warn(
        "Failed to cancel operation {} among {} in job {}", opId, operations.keySet(), jobId);
    return false;
  }

  @Override
  public JobState state() {
    return state;
  }

  @Override
  public void yield() {
    // Lock and immediately unlock to respect job suspension which holds this lock.
    lock.lock();
    lock.unlock();
  }

  @Override
  public Queue<Message> renderQueue() {
    return jobRenderQueue;
  }

  @Override
  public Queue<Message> tickQueue() {
    return jobTickQueue;
  }

  @Override
  public boolean respond(long functionCallId, JsonElement returnValue, boolean finalReply) {
    boolean result = task.sendResponse(functionCallId, returnValue, finalReply);
    if (functionCallId == 0
        && returnValue.isJsonPrimitive()
        && returnValue instanceof JsonPrimitive primitive
        && primitive.isString()
        && primitive.getAsString().equals("exit!")) {
      state = JobState.DONE;
    }
    return result;
  }

  @Override
  public boolean raiseException(long functionCallId, ExceptionInfo exception) {
    return task.sendException(functionCallId, exception);
  }

  @Override
  public void processStdout(String text) {
    // Stdout lines with FUNCTION_PREFIX are handled as function calls regardless of redirection.
    if (text.startsWith(FUNCTION_PREFIX)) {
      processFunctionCall(text.substring(FUNCTION_PREFIX.length()));
      return;
    }

    switch (command.redirects().stdout()) {
      case CHAT:
        if (text.startsWith("/")) {
          jobTickQueue.add(Message.createMinecraftCommand(text.substring(1)));
        } else if (text.startsWith("\\")) {
          jobTickQueue.add(Message.createMinescriptCommand(text.substring(1)));
        } else {
          jobTickQueue.add(Message.createChatMessage(text));
        }
        break;
      case DEFAULT:
      case ECHO:
        jobTickQueue.add(Message.fromPlainText(text));
        break;
      case LOG:
        LOGGER.info(text);
        break;
      case NULL:
        break;
    }
  }

  private void processFunctionCall(String functionCallLine) {
    // Function call messages have values formatted as:
    // "{funcCallId} {functionName} {argsString}"
    //
    // argsString may have spaces, e.g. "123 my_func [4, 5, 6]"

    String[] functionCall = functionCallLine.split("\\s+", 4);
    long funcCallId = Long.valueOf(functionCall[0]);
    final FunctionExecutor executor;
    try {
      executor = FunctionExecutor.fromValue(functionCall[1]);
    } catch (IllegalArgumentException e) {
      raiseException(funcCallId, ExceptionInfo.fromException(e));
      return;
    }
    String functionName = functionCall[2];
    String argsString = functionCall[3];

    final List<?> args;
    try {
      args = GSON.fromJson(argsString, ArrayList.class);
    } catch (JsonSyntaxException e) {
      String exceptionMessage =
          String.format(
              "Syntax error in script function args to `%s`: `%s`. This is likely"
                  + " caused by unsynchronized output printed to stdout elsewhere"
                  + " in this script. If the error persists, try replacing those"
                  + " raw print() calls with minescript.echo() or printing to"
                  + " stderr instead.",
              functionName, functionCallLine);
      raiseException(
          funcCallId, ExceptionInfo.fromException(new IllegalArgumentException(exceptionMessage)));
      return;
    }

    if (executor == FunctionExecutor.SCRIPT_LOOP
        || (executor == FunctionExecutor.DEFAULT
            && config.scriptLoopFunctions().contains(functionName))) {
      Minescript.processScriptFunction(this, functionName, funcCallId, argsString, args);
      return;
    }

    if (executor == FunctionExecutor.RENDER_LOOP
        || (executor == FunctionExecutor.DEFAULT
            && config.renderLoopFunctions().contains(functionName))) {
      jobRenderQueue.add(
          Message.createFunctionCall(funcCallId, executor, functionName, argsString, args));
      return;
    }

    jobTickQueue.add(
        Message.createFunctionCall(funcCallId, executor, functionName, argsString, args));
  }

  @Override
  public void processStderr(String text) {
    if (config.stderrChatIgnorePattern().matcher(text).find()) {
      return;
    }

    switch (command.redirects().stderr()) {
      case CHAT:
        if (text.startsWith("/")) {
          jobTickQueue.add(Message.createMinecraftCommand(text.substring(1)));
        } else if (text.startsWith("\\")) {
          jobTickQueue.add(Message.createMinescriptCommand(text.substring(1)));
        } else {
          jobTickQueue.add(Message.createChatMessage(text));
        }
        break;
      case DEFAULT:
      case ECHO:
        jobTickQueue.add(Message.formatAsJsonColoredText(text, "yellow"));
        break;
      case LOG:
        LOGGER.info(text);
        break;
      case NULL:
        break;
    }
  }

  @Override
  public void logJobException(Exception e) {
    var sw = new StringWriter();
    var pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    systemMessageQueue.logUserError(
        "Exception in job `{}`: {} (see logs/latest.log for details)", jobSummary(), e.toString());
    LOGGER.error("exception stack trace in job `{}`: {}", jobSummary(), sw.toString());
  }

  public void start() {
    thread =
        new Thread(this::runOnJobThread, String.format("job-%d-%s", jobId, command.command()[0]));
    thread.start();
  }

  public boolean suspend() {
    if (state == JobState.KILLED) {
      systemMessageQueue.logUserError("Job already killed: {}", jobSummary());
      return false;
    }
    if (state == JobState.SUSPENDED) {
      systemMessageQueue.logUserError("Job already suspended: {}", jobSummary());
      return false;
    }
    try {
      int timeoutSeconds = 2;
      if (lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
        state = JobState.SUSPENDED;
        suspendOperations();
        return true;
      } else {
        systemMessageQueue.logUserError(
            "Timed out trying to suspend job after {} seconds: {}", timeoutSeconds, jobSummary());
        return false;
      }
    } catch (InterruptedException e) {
      systemMessageQueue.logException(e);
      return false;
    }
  }

  private void suspendOperations() {
    for (var op : operations.values()) {
      op.suspend();
    }
  }

  public boolean resume() {
    if (state != JobState.SUSPENDED && state != JobState.KILLED) {
      systemMessageQueue.logUserError("Job not suspended: {}", jobSummary());
      return false;
    }
    if (state == JobState.SUSPENDED) {
      state = JobState.RUNNING;
      resumeOperations();
    }
    try {
      lock.unlock();
    } catch (IllegalMonitorStateException e) {
      systemMessageQueue.logException(e);
      return false;
    }
    return true;
  }

  private void resumeOperations() {
    var iter = operations.values().iterator();
    while (iter.hasNext()) {
      var op = iter.next();
      if (op.resumeAndCheckDone()) {
        iter.remove();
      }
    }
  }

  public void kill() {
    JobState prevState = state;
    state = JobState.KILLED;
    if (prevState == JobState.SUSPENDED) {
      resume();
    }
  }

  private void runOnJobThread() {
    if (state == JobState.NOT_STARTED) {
      state = JobState.RUNNING;
    }
    try {
      final long startTimeMillis = System.currentTimeMillis();
      final long longRunningJobThreshold = 3000L;
      int exitCode = task.run(command, this);

      final int millisToSleep = 1000;
      while (state != JobState.KILLED
          && state != JobState.DONE
          && !jobTickQueue.isEmpty()
          && !jobRenderQueue.isEmpty()) {
        try {
          Thread.sleep(millisToSleep);
        } catch (InterruptedException e) {
          logJobException(e);
        }
      }
      final long endTimeMillis = System.currentTimeMillis();
      if (exitCode != 0) {
        systemMessageQueue.logUserError(jobSummaryWithStatus("Exited with error code " + exitCode));
      } else if (endTimeMillis - startTimeMillis > longRunningJobThreshold) {
        if (state != JobState.KILLED) {
          state = JobState.DONE;
        }
        systemMessageQueue.logUserInfo(toString());
      }
    } finally {
      for (Operation op : operations.values()) {
        op.cancel();
      }
      operations.clear();

      objects.releaseAll();
      blockpacks.releaseAll();
      blockpackers.releaseAll();

      doneCallback.accept(jobId);
    }
  }

  @Override
  public int jobId() {
    return jobId;
  }

  public String jobSummary() {
    return jobSummaryWithStatus("");
  }

  private String jobSummaryWithStatus(String status) {
    String displayCommand = quoteCommand(command.command());
    if (displayCommand.length() > 61) {
      displayCommand = displayCommand.substring(0, 61) + "...";
    }
    return String.format(
        "[%d] %s%s%s", jobId, status, status.isEmpty() ? "" : ": ", displayCommand);
  }

  @Override
  public String toString() {
    return jobSummaryWithStatus(state.toString());
  }
}
