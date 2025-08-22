// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.List;

public interface ScriptFunctionRunner {
  void run(Job.SubprocessJob job, String functionName, long funcCallId, List<?> parsedArgs);
}
