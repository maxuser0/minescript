// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

/** Snapshot of the in-game player list. */
public class TabListData extends Jsonable {
  private final TabListPlayerData[] players;
  private final TabListTextData header;
  private final TabListTextData footer;
  private final TabListObjectiveData objective;

  public TabListData(
      TabListPlayerData[] players,
      TabListTextData header,
      TabListTextData footer,
      TabListObjectiveData objective) {
    this.players = players;
    this.header = header;
    this.footer = footer;
    this.objective = objective;
  }

  public TabListPlayerData[] players() {
    return players;
  }

  public TabListTextData header() {
    return header;
  }

  public TabListTextData footer() {
    return footer;
  }

  public TabListObjectiveData objective() {
    return objective;
  }
}
