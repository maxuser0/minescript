// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import com.google.gson.JsonElement;
import net.minescript.common.Jsonable;

/** Plain and structured representations of text displayed in the player list. */
public class TabListTextData extends Jsonable {
  public final String plain;
  public final JsonElement json;

  public TabListTextData(String plain, JsonElement json) {
    this.plain = plain;
    this.json = json;
  }

  public String[] lines() {
    return plain.lines().toArray(String[]::new);
  }
}
