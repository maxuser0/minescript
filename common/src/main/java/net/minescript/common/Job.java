// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.quoteCommand;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
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
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Job implements JobControl {
  private static final Logger LOGGER = LogManager.getLogger();

  public final int jobId;
  public final ResourceTracker<BlockPack> blockpacks;
  public final ResourceTracker<BlockPacker> blockpackers;

  private final ScriptConfig.BoundCommand command;
  private final Task task;
  private final Pattern stderrChatIgnorePattern;
  private final SystemMessageQueue systemMessageQueue;
  private Thread thread;
  private volatile JobState state = JobState.NOT_STARTED;
  private Consumer<Integer> doneCallback;
  private Queue<Message> jobMessageQueue = new ConcurrentLinkedQueue<Message>();
  private Lock lock = new ReentrantLock(true); // true indicates a fair lock to avoid starvation
  private Map<Long, Operation> operations = new ConcurrentHashMap<>(); // key is func call id
  private List<Runnable> atExitHandlers = new ArrayList<>();

  // Special prefix for commands and function calls emitted from stdout of scripts, for example:
  // - script function call: "?mnsc:123 my_func [4, 5, 6]"
  // - script system call: "?mnsc:0 exit! []"
  private static final String FUNCTION_PREFIX = "?mnsc:";

  public interface Operation {
    void suspend();

    boolean resumeAndCheckDone();

    void cancel();
  }

  public Job(
      int jobId,
      ScriptConfig.BoundCommand command,
      Task task,
      Pattern stderrChatIgnorePattern,
      SystemMessageQueue systemMessageQueue,
      Consumer<Integer> doneCallback) {
    this.jobId = jobId;
    this.command = command;
    this.task = task;
    this.stderrChatIgnorePattern = stderrChatIgnorePattern;
    this.systemMessageQueue = systemMessageQueue;
    this.doneCallback = doneCallback;
    blockpacks = new ResourceTracker<>(BlockPack.class, jobId);
    blockpackers = new ResourceTracker<>(BlockPacker.class, jobId);
  }

  public void addOperation(long funcCallId, Operation op) {
    operations.put(funcCallId, op);
  }

  public void removeOperation(long funcCallId) {
    operations.remove(funcCallId);
  }

  /** Attempts to cancel operation associated with funcCallId, returning true if found. */
  public boolean cancelOperation(long funcCallId) {
    var op = operations.get(funcCallId);
    if (op != null) {
      op.cancel();
      return true;
    }
    return false;
  }

  public void addAtExitHandler(Runnable handler) {
    atExitHandlers.add(handler);
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
  public Queue<Message> messageQueue() {
    return jobMessageQueue;
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
  public void raiseException(long functionCallId, ExceptionInfo exception) {
    task.sendException(functionCallId, exception);
  }

  @Override
  public void processStdout(String text) {
    // Stdout lines with FUNCTION_PREFIX are handled as function calls regardless of redirection.
    if (text.startsWith(FUNCTION_PREFIX)) {
      jobMessageQueue.add(Message.createFunctionCall(text.substring(FUNCTION_PREFIX.length())));
      return;
    }

    switch (command.redirects().stdout()) {
      case CHAT:
        if (text.startsWith("/")) {
          jobMessageQueue.add(Message.createMinecraftCommand(text.substring(1)));
        } else if (text.startsWith("\\")) {
          jobMessageQueue.add(Message.createMinescriptCommand(text.substring(1)));
        } else {
          jobMessageQueue.add(Message.createChatMessage(text));
        }
        break;
      case DEFAULT:
      case ECHO:
        jobMessageQueue.add(Message.fromPlainText(text));
        break;
      case LOG:
        LOGGER.info(text);
        break;
      case NULL:
        break;
    }
  }

  @Override
  public void processStderr(String text) {
    if (stderrChatIgnorePattern.matcher(text).find()) {
      return;
    }

    switch (command.redirects().stderr()) {
      case CHAT:
        if (text.startsWith("/")) {
          jobMessageQueue.add(Message.createMinecraftCommand(text.substring(1)));
        } else if (text.startsWith("\\")) {
          jobMessageQueue.add(Message.createMinescriptCommand(text.substring(1)));
        } else {
          jobMessageQueue.add(Message.createChatMessage(text));
        }
        break;
      case DEFAULT:
      case ECHO:
        jobMessageQueue.add(Message.formatAsJsonColoredText(text, "yellow"));
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
      while (state != JobState.KILLED && state != JobState.DONE && !jobMessageQueue.isEmpty()) {
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

      for (Runnable handler : atExitHandlers) {
        handler.run();
      }
      atExitHandlers.clear();

      blockpacks.releaseAll();
      blockpackers.releaseAll();

      doneCallback.accept(jobId);
    }
  }

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
