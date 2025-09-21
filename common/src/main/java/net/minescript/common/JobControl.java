// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.Optional;
import java.util.Queue;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * Provides an interface for managing and interacting with a running job (e.g., a script or
 * command).
 *
 * <p>This interface allows for controlling the lifecycle of a job (suspending, resuming, killing),
 * queuing messages, handling I/O, and managing asynchronous operations within the job.
 */
public interface JobControl {
  int jobId();

  Optional<Integer> parentJobId();

  ScriptConfig.BoundCommand boundCommand();

  JobState state();

  String jobSummary();

  void yield();

  boolean suspend();

  boolean resume();

  void requestKill();

  Queue<Message> renderQueue();

  Queue<Message> tickQueue();

  boolean respond(long functionCallId, ScriptValue value, boolean finalReply);

  boolean raiseException(long functionCallId, Exception exception);

  void processStdout(String text);

  void processStderr(String text);

  interface Operation {
    String name();

    void suspend();

    boolean resumeAndCheckDone();

    void cancel();
  }

  void addOperation(long opId, Operation op);

  boolean cancelOperation(long opId);

  default void log(String messagePattern, Object... arguments) {
    String logMessage =
        (arguments.length == 0)
            ? messagePattern
            : ParameterizedMessage.format(messagePattern, arguments);
    processStderr(logMessage);
  }

  void logJobException(Exception e);
}
