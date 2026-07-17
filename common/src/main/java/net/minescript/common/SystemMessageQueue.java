// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

public class SystemMessageQueue {
  private static final Logger LOGGER = LogManager.getLogger();

  private Queue<Message> queue = new ConcurrentLinkedQueue<Message>();

  public void add(Message message) {
    queue.add(message);
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public Message poll() {
    return queue.poll();
  }

  public void clear() {
    queue.clear();
  }

  public void logUserInfo(String messagePattern, Object... arguments) {
    String logMessage = ParameterizedMessage.format(messagePattern, arguments);
    LOGGER.info("{}", logMessage);
    queue.add(Message.formatAsJsonColoredText(logMessage, "yellow"));
  }

  public void logUserError(String messagePattern, Object... arguments) {
    String logMessage = ParameterizedMessage.format(messagePattern, arguments);
    LOGGER.error("{}", logMessage);
    queue.add(Message.formatAsJsonColoredText(logMessage, "red"));
  }

  public void logException(Exception e) {
    logException(
        e,
        String.format(
            "Minescript internal error: %s (see logs/latest.log for details; to browse or report"
                + " issues see https://minescript.net/issues)",
            e.toString()));
  }

  public void logException(Exception e, String chatMessage) {
    var sw = new StringWriter();
    var pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    logUserError(chatMessage);
    LOGGER.error(sw.toString());
  }
}
