// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class ScoreboardData extends Jsonable {
  public String objective_name;
  public String display_name;
  public ScoreboardEntry[] entries;

  public ScoreboardData(String objectiveName, String displayName, ScoreboardEntry[] entries) {
    this.objective_name = objectiveName;
    this.display_name = displayName;
    this.entries = entries;
  }
}
