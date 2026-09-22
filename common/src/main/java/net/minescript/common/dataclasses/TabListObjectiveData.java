// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

/** Scoreboard objective displayed in the player list, if present. */
public class TabListObjectiveData extends Jsonable {
  public final String name;
  public final TabListTextData display_name;
  public final String render_type;

  public TabListObjectiveData(String name, TabListTextData displayName, String renderType) {
    this.name = name;
    this.display_name = displayName;
    this.render_type = renderType;
  }
}
