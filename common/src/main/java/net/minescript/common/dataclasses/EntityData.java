// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class EntityData extends Jsonable {
  public String name;
  public String type;
  public String uuid;
  public int id;
  public Float health = null;
  public Boolean local = null;
  public double[] position = new double[3];
  public double[] lerp_position = null;
  public float yaw;
  public float pitch;
  public double[] velocity = new double[3];
  public String[] passengers = null;
  public String nbt = null;
}
