// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.quoteCommand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Job implements JobControl {

  // TODO(maxuser): Move SubprocessJob to its own file.
  public static class SubprocessJob extends Job {
    private final ScriptFunctionRunner scriptFunctionRunner;
    private Thread thread;
    public final ResourceTracker<Object> objects;
    public final ResourceTracker<BlockPack> blockpacks;
    public final ResourceTracker<BlockPacker> blockpackers;

    // Special prefix for commands and function calls emitted from stdout of scripts, for example:
    // - script function call: "?mnsc:123 X my_func [4, 5, 6]"
    // - script system call: "?mnsc:0 X exit! []"
    private static final String FUNCTION_PREFIX = "?mnsc:";

    public SubprocessJob(
        int jobId,
        Optional<Integer> parentJobId,
        ScriptConfig.BoundCommand command,
        Task task,
        Config config,
        SystemMessageQueue systemMessageQueue,
        ScriptFunctionRunner scriptFunctionRunner,
        Runnable doneCallback) {
      super(jobId, parentJobId, command, task, config, systemMessageQueue, null, doneCallback);

      this.scriptFunctionRunner = scriptFunctionRunner;
      this.objects = new ResourceTracker<>(Object.class, jobId, config);
      this.blockpacks = new ResourceTracker<>(BlockPack.class, jobId, config);
      this.blockpackers = new ResourceTracker<>(BlockPacker.class, jobId, config);
    }

    @Override
    protected void start() {
      thread =
          new Thread(
              () -> runOnJobThread(this, task, systemMessageQueue, config),
              String.format("job-%d-%s", jobId, command.command()[0]));
      thread.start();
    }

    private static void runOnJobThread(
        Job job, Task task, SystemMessageQueue systemMessageQueue, Config config) {
      if (job.state() == JobState.NOT_STARTED) {
        job.setState(JobState.RUNNING);
      }
      try {
        final long startTimeMillis = System.currentTimeMillis();
        int exitCode = task.run(job.boundCommand(), job);
        LOGGER.info(
            "Job `{}` exited with code {}, draining message queues...", job.jobSummary(), exitCode);

        final int millisToSleep = 1000;
        while (!(job.state() == JobState.KILLED
            || (job.tickQueue().isEmpty() && job.renderQueue().isEmpty()))) {
          try {
            Thread.sleep(millisToSleep);
          } catch (InterruptedException e) {
            job.logJobException(e);
          }
        }
        final long endTimeMillis = System.currentTimeMillis();
        final long reportJobSuccessThresholdMillis = config.reportJobSuccessThresholdMillis();
        if (exitCode != 0) {
          systemMessageQueue.logUserError(
              job.jobSummaryWithStatus("Exited with error code " + exitCode));
        } else if (reportJobSuccessThresholdMillis >= 0
            && endTimeMillis - startTimeMillis > reportJobSuccessThresholdMillis) {
          if (job.state() != JobState.KILLED) {
            job.setState(JobState.DONE);
          }
          systemMessageQueue.logUserInfo(job.toString());
        }
      } finally {
        job.close();
      }
    }

    @Override
    protected boolean handleStdout(String text) {
      // Stdout lines with FUNCTION_PREFIX are handled as function calls regardless of redirection.
      if (text.startsWith(FUNCTION_PREFIX)) {
        processFunctionCall(text.substring(FUNCTION_PREFIX.length()));
        return true;
      }
      return false;
    }

    private void processFunctionCall(String functionCallLine) {
      // Function call messages have values formatted as:
      // "{funcCallId} {executor} {functionName} {args}"
      //
      // args may have spaces, e.g. "123 R my_func [4, 5, 6]"

      String[] functionCall = functionCallLine.split("\\s+", 4);
      long funcCallId = Long.valueOf(functionCall[0]);
      final FunctionExecutor executor;
      try {
        executor = FunctionExecutor.fromValue(functionCall[1]);
      } catch (IllegalArgumentException e) {
        raiseException(funcCallId, e);
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
        raiseException(funcCallId, new IllegalArgumentException(exceptionMessage));
        return;
      }

      if (executor == FunctionExecutor.SCRIPT_LOOP) {
        scriptFunctionRunner.run(this, functionName, funcCallId, args);
        return;
      }

      if (executor == FunctionExecutor.RENDER_LOOP) {
        renderQueue().add(Message.createFunctionCall(funcCallId, executor, functionName, args));
        return;
      }

      tickQueue().add(Message.createFunctionCall(funcCallId, executor, functionName, args));
    }

    @Override
    protected void onClose() {
      objects.releaseAll();
      blockpacks.releaseAll();
      blockpackers.releaseAll();
    }
  }

  private static final Logger LOGGER = LogManager.getLogger();
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  protected final int jobId;
  protected final Optional<Integer> parentJobId;
  protected final ScriptConfig.BoundCommand command;
  protected final Task task;
  protected final Config config;
  protected final SystemMessageQueue systemMessageQueue;
  private volatile JobState state = JobState.NOT_STARTED;
  private final Consumer<Message> messageConsumer;
  private final Runnable doneCallback;
  private final Queue<Message> jobTickQueue = new ConcurrentLinkedQueue<Message>();
  private final Queue<Message> jobRenderQueue = new ConcurrentLinkedQueue<Message>();
  private final Lock lock =
      new ReentrantLock(true); // true indicates a fair lock to avoid starvation

  // Key is unique within this job, typically a func call ID.
  private Map<Long, Operation> operations = new ConcurrentHashMap<>();

  protected Job(
      int jobId,
      Optional<Integer> parentJobId,
      ScriptConfig.BoundCommand command,
      Task task,
      Config config,
      SystemMessageQueue systemMessageQueue,
      Consumer<Message> messageConsumer,
      Runnable doneCallback) {
    this.jobId = jobId;
    this.parentJobId = parentJobId;
    this.command = command;
    this.task = task;
    this.config = config;
    this.systemMessageQueue = systemMessageQueue;
    this.messageConsumer = messageConsumer == null ? jobTickQueue::add : messageConsumer;
    this.doneCallback = doneCallback;
  }

  @Override
  public ScriptConfig.BoundCommand boundCommand() {
    return command;
  }

  @Override
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
  @Override
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
  public boolean respond(long functionCallId, ScriptValue returnValue, boolean finalReply) {
    boolean result = task.sendResponse(functionCallId, returnValue, finalReply);
    if (functionCallId == 0
        && returnValue.get() instanceof String string
        && string.equals("exit!")) {
      if (config.debugOutput()) {
        LOGGER.info("Job {} got `exit!`, setting state to DONE", jobId);
      }
      state = JobState.DONE;
    }
    return result;
  }

  @Override
  public boolean raiseException(long functionCallId, Exception exception) {
    return task.sendException(functionCallId, exception);
  }

  protected boolean handleStdout(String text) {
    return false;
  }

  @Override
  public void processStdout(String text) {
    if (handleStdout(text)) {
      return;
    }

    switch (command.redirects().stdout()) {
      case CHAT:
        if (text.startsWith("/")) {
          messageConsumer.accept(Message.createMinecraftCommand(text.substring(1)));
        } else if (text.startsWith("\\")) {
          messageConsumer.accept(Message.createMinescriptCommand(text.substring(1)));
        } else {
          messageConsumer.accept(Message.createChatMessage(text));
        }
        break;
      case DEFAULT:
      case ECHO:
        messageConsumer.accept(Message.fromPlainText(text));
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
    if (config.stderrChatIgnorePattern().matcher(text).find()) {
      return;
    }

    switch (command.redirects().stderr()) {
      case CHAT:
        if (text.startsWith("/")) {
          messageConsumer.accept(Message.createMinecraftCommand(text.substring(1)));
        } else if (text.startsWith("\\")) {
          messageConsumer.accept(Message.createMinescriptCommand(text.substring(1)));
        } else {
          messageConsumer.accept(Message.createChatMessage(text));
        }
        break;
      case DEFAULT:
      case ECHO:
        messageConsumer.accept(Message.formatAsJsonColoredText(text, "yellow"));
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

  protected abstract void start();

  @Override
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

  @Override
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

  @Override
  public void requestKill() {
    JobState prevState = state;
    state = JobState.KILLED;
    if (prevState == JobState.SUSPENDED) {
      resume();
    }
  }

  protected void setState(JobState state) {
    this.state = state;
  }

  protected abstract void onClose();

  protected final void close() {
    for (Operation op : operations.values()) {
      op.cancel();
    }
    operations.clear();
    onClose();
    doneCallback.run();
  }

  @Override
  public int jobId() {
    return jobId;
  }

  @Override
  public Optional<Integer> parentJobId() {
    return parentJobId;
  }

  @Override
  public final String jobSummary() {
    return jobSummaryWithStatus("");
  }

  protected final String jobSummaryWithStatus(String status) {
    String displayCommand = quoteCommand(command.command());
    if (displayCommand.length() > 61) {
      displayCommand = displayCommand.substring(0, 61) + "...";
    }
    return String.format(
        "[%d%s] %s%s%s",
        jobId,
        parentJobId.map(id -> ", parent=%d".formatted(id)).orElse(""),
        status,
        status.isEmpty() ? "" : ": ",
        displayCommand);
  }

  @Override
  public String toString() {
    return jobSummaryWithStatus(state.toString());
  }
}
