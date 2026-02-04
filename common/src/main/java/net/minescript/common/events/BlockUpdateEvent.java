// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class BlockUpdateEvent extends Jsonable {
  public String type = "block_update";
  public int[] position = new int[3];
  public String old_state;
  public String new_state;
  public double time;
}
