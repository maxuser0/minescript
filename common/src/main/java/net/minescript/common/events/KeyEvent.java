// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class KeyEvent extends Jsonable {
  public String type = "key";
  public int key;
  public int scan_code;
  public int action;
  public int modifiers;
  public double time;
  public String screen;
}
