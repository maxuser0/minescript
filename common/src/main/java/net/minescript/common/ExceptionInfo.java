// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.List;
import com.google.common.collect.ImmutableList;

public record ExceptionInfo(String type, String message, String desc, List<StackElement> stack) {

  public record StackElement(String file, String method, int line) {}

  public static ExceptionInfo fromException(Exception e) {
    var type = e.getClass().getName();
    var desc = e.toString();
    var stackBuilder = new ImmutableList.Builder<StackElement>();
    boolean hitMinescriptJava = false;
    for (var element : e.getStackTrace()) {
      var filename = element.getFileName();
      // Capture stacktrace up through Minescript.java, but no further.
      if (!hitMinescriptJava && filename.equals("Minescript.java")) {
        hitMinescriptJava = true;
      } else if (hitMinescriptJava && !filename.equals("Minescript.java")) {
        break;
      }
      stackBuilder.add(
          new StackElement(filename, element.getMethodName(), element.getLineNumber()));
    }
    return new ExceptionInfo(type, e.getMessage(), desc, stackBuilder.build());
  }
}
