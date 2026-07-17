// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

public abstract class Jsonable {
  public JsonElement toJson() {
    return new GsonBuilder().serializeNulls().create().toJsonTree(this);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + toJson().toString() + ")";
  }
}
