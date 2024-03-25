// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.List;

public interface ScriptFunctionRunner {
  void run(Job job, String functionName, long funcCallId, List<?> parsedArgs);
}
