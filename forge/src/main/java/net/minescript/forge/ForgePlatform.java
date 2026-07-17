// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.forge;

import net.minescript.common.Platform;

class ForgePlatform implements Platform {
  @Override
  public String modLoaderName() {
    return "Forge";
  }
}
