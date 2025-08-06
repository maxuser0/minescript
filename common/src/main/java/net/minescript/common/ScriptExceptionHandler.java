// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;

class ScriptExceptionHandler {
  private static final Logger LOGGER = LogManager.getLogger();

  public static void reportException(SystemMessageQueue systemMessageQueue, Throwable e) {
    systemMessageQueue.logUserError(prettyPrintScriptException(e, /* toplevelException= */ true));
    LOGGER.error("Caught script exception:", e);
  }

  private static String prettyPrintScriptException(Throwable e, boolean toplevelException) {
    var out = new StringBuilder();
    if (toplevelException) {
      out.append("Traceback (most recent call last):\n");
    }
    var frames = e.getStackTrace();
    for (int i = frames.length - 1; i >= 0; --i) {
      var frame = frames[i];
      var filename = frame.getFileName();
      if (filename.toLowerCase().endsWith(".py") || filename.toLowerCase().endsWith(".pyj")) {
        out.append("  File \"%s\", line %d\n".formatted(filename, frame.getLineNumber()));
      }
    }

    Throwable cause = e.getCause();
    if (cause == null) {
      // Print the exception message for only the root cause.
      if (e instanceof Script.InterpreterException interpreterException) {
        out.append(interpreterException.getShortMessage());
      } else {
        out.append(e.toString());
      }
      out.append("\n");
    } else {
      out.append(prettyPrintScriptException(cause, /* toplevelException= */ false));
    }
    return out.toString();
  }
}
