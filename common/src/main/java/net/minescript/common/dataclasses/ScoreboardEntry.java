// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class ScoreboardEntry extends Jsonable {
  public String name;
  public int score;
  public String display_name;

  public ScoreboardEntry(String name, int score, String displayName) {
    this.name = name;
    this.score = score;
    this.display_name = displayName;
  }
}
