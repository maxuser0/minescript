// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.JsonElement;

public interface Task {
  int run(ScriptConfig.BoundCommand command, JobControl jobControl);

  default boolean sendResponse(long functionCallId, JsonElement returnValue, boolean finalReply) {
    return false;
  }

  default boolean sendException(long functionCallId, ExceptionInfo exception) {
    return false;
  }
}
