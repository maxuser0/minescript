// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class ExplosionEvent extends Jsonable {
  public String type = "explosion";
  public double[] position = new double[3];
  public String blockpack_base64;
  public double time;
}
