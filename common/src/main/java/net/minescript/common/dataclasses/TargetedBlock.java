// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class TargetedBlock extends Jsonable {
  public int[] position = new int[3];
  public double distance;
  public String side;
  public String type;
}
