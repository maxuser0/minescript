// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;
import net.minescript.common.dataclasses.EntityData;

public class AddEntityEvent extends Jsonable {
  public String type = "add_entity";
  public EntityData entity;
  public double time;
}
