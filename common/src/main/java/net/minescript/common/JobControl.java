// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.JsonElement;
import java.util.Queue;
import org.apache.logging.log4j.message.ParameterizedMessage;

public interface JobControl {
  int jobId();

  JobState state();

  void yield();

  Queue<Message> renderQueue();

  Queue<Message> tickQueue();

  boolean respond(long functionCallId, JsonElement returnValue, boolean finalReply);

  boolean raiseException(long functionCallId, ExceptionInfo exception);

  void processStdout(String text);

  void processStderr(String text);

  default void log(String messagePattern, Object... arguments) {
    String logMessage =
        (arguments.length == 0)
            ? messagePattern
            : ParameterizedMessage.format(messagePattern, arguments);
    processStderr(logMessage);
  }

  void logJobException(Exception e);
}
