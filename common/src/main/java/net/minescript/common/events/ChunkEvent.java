// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class ChunkEvent extends Jsonable {
  public String type = "chunk";
  public boolean loaded;
  public int x_min;
  public int z_min;
  public int x_max;
  public int z_max;
  public double time;
}
