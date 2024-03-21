// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

public enum FunctionExecutor {
  TICK_LOOP("T"), // Process script functions 20 times per second with game ticks.
  RENDER_LOOP("R"), // Process script functions at the rendering frame rate, about 30-100 fps.
  SCRIPT_LOOP("S"); // Process script functions on the subprocess IO thread, >1000x per second.

  private final String value;

  FunctionExecutor(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static FunctionExecutor fromValue(String value) {
    switch (value) {
      case "T":
        return TICK_LOOP;
      case "R":
        return RENDER_LOOP;
      case "S":
        return SCRIPT_LOOP;
      default:
        throw new IllegalArgumentException("No FunctionExecutor for value: " + value);
    }
  }
}
