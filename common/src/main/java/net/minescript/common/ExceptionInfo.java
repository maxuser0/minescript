// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

public record ExceptionInfo(
    String type,
    String message,
    String desc,
    List<StackElement> stack,
    /* nullable */ ExceptionInfo cause) {

  public record StackElement(String file, String method, int line) {}

  public static ExceptionInfo fromException(Throwable e) {
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
    var cause = Optional.ofNullable(e.getCause()).map(ExceptionInfo::fromException);
    return new ExceptionInfo(type, e.getMessage(), desc, stackBuilder.build(), cause.orElse(null));
  }
}
