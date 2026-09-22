// SPDX-FileCopyrightText: © 2026 jkramer5103 <info@jkramertech.com>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

final class SubprocessProcessBuilder {
  private SubprocessProcessBuilder() {}

  static ProcessBuilder create(String[] command, String[] environmentVariables) {
    var processBuilder = new ProcessBuilder(command);
    var environment = processBuilder.environment();
    for (String variable : environmentVariables) {
      int separator = variable.indexOf('=');
      if (separator < 1) {
        throw new IllegalArgumentException("Invalid environment variable: " + variable);
      }
      environment.put(variable.substring(0, separator), variable.substring(separator + 1));
    }
    return processBuilder;
  }
}
