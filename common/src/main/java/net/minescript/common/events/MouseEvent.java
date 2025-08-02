// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class MouseEvent extends Jsonable {
  public String type = "mouse";
  public int button;
  public int action;
  public int modifiers;
  public double time;
  public double x;
  public double y;
  public String screen;
}
