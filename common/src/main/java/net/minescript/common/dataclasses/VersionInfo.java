// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import net.minescript.common.Jsonable;

public class VersionInfo extends Jsonable {
  public String minecraft;
  public String minescript;
  public String mod_loader;
  public String launcher;
  public String os_name;
  public String os_version;
  public String minecraft_class_name;
  public String pyjinn;
}
