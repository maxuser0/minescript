// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class BlockRegion extends Jsonable {
  public int[] min_pos;
  public int[] max_pos;
  public String[] blocks;
}
