// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.ImmutableList;
import java.util.List;

public record ExceptionInfo(String type, String message, String desc, List<StackElement> stack) {

  public record StackElement(String file, String method, int line) {}

  public static ExceptionInfo fromException(Exception e) {
    var type = e.getClass().getName();
    var desc = e.toString();
    var stackBuilder = new ImmutableList.Builder<StackElement>();
    boolean hitMinescriptJava = false;
    int stackDepth = 0;
    for (var element : e.getStackTrace()) {
      if (++stackDepth > 10) {
        break;
      }
      var className = element.getClassName();
      var fileName = element.getFileName();
      if (className != null) {
        // Capture stacktrace up through Minescript code, but no further.
        if (!hitMinescriptJava && className.contains("minescript")) {
          hitMinescriptJava = true;
        } else if (hitMinescriptJava && !className.contains("minescript")) {
          break;
        }
      }
      stackBuilder.add(
          new StackElement(fileName, element.getMethodName(), element.getLineNumber()));
    }
    return new ExceptionInfo(type, e.getMessage(), desc, stackBuilder.build());
  }
}
