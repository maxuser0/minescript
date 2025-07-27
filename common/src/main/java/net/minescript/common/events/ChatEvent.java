// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class ChatEvent extends Jsonable {
  public String type = "chat";
  public String message;
  public double time;
}
