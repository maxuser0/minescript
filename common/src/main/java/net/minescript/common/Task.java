// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

public interface Task {
  int run(ScriptConfig.BoundCommand command, JobControl jobControl);

  /** Sends a return value to the given script function call. Returns true if response succeeds. */
  default boolean sendResponse(long functionCallId, ScriptValue scriptValue, boolean finalReply) {
    return false;
  }

  /** Sends an exception to the given script function call. Returns true if response succeeds. */
  default boolean sendException(long functionCallId, Exception exception) {
    return false;
  }
}
