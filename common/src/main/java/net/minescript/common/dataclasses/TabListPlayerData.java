// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

/** Snapshot of an entry displayed in the in-game player list. */
public class TabListPlayerData extends Jsonable {
  public String uuid;
  public String name;
  public TabListTextData display_name;
  public int latency;
  public String game_mode;
  public String team = null;
  public int order;
  public String skin_texture;
  public boolean show_hat;
  public Integer score = null;
  public TabListTextData score_display = null;
}
