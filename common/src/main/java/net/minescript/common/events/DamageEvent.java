// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minescript.common.Jsonable;

public class DamageEvent extends Jsonable {
  public String type = "damage";
  public String entity_uuid;
  public String cause_uuid;
  public String source;
  public double time;
}
