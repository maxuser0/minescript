// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

public enum JobState {
  NOT_STARTED("Not started"),
  RUNNING("Running"),
  SUSPENDED("Suspended"),
  KILLED("Killed"),
  DONE("Done");

  private final String displayName;

  private JobState(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
};
