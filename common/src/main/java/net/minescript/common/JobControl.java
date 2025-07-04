// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.JsonElement;
import java.util.Queue;
import org.apache.logging.log4j.message.ParameterizedMessage;

public interface JobControl {
  int jobId();

  ScriptConfig.BoundCommand boundCommand();

  JobState state();

  String jobSummary();

  void yield();

  boolean suspend();

  boolean resume();

  void requestKill();

  Queue<Message> renderQueue();

  Queue<Message> tickQueue();

  boolean respond(long functionCallId, JsonElement returnValue, boolean finalReply);

  boolean raiseException(long functionCallId, ExceptionInfo exception);

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
