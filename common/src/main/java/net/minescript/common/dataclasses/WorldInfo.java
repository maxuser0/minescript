// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class WorldInfo extends Jsonable {
  public long game_ticks;
  public long day_ticks;
  public boolean raining;
  public boolean thundering;
  public int[] spawn = new int[3];
  public boolean hardcore;
  public String difficulty;
  public String name;
  public String address;
}
