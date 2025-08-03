// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mappings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MappingsLoader {
  private static final Logger LOGGER = LogManager.getLogger();

  private final String modLoaderName;
  private final String mcVersion;
  private final boolean isMinecraftClassObfuscated;

  private NameMappings nameMappings;

  public MappingsLoader(
      String mcVersion, String modLoaderName, boolean isMinecraftClassObfuscated) {
    this.mcVersion = mcVersion;
    this.modLoaderName = modLoaderName;
    this.isMinecraftClassObfuscated = isMinecraftClassObfuscated;
  }

  public void load() throws Exception {
    final NameMappings nameMappings;
    // Check for obfuscated names, and if needed, load a mappings file.
    if (isMinecraftClassObfuscated) {
      long loadStartTime = System.currentTimeMillis();
      var obfuscatedMappings = ObfuscatedNameMappings.loadFromFiles(modLoaderName, mcVersion);
      if (obfuscatedMappings.isPresent()) {
        long loadEndTime = System.currentTimeMillis();
        LOGGER.info("Loaded mappings for deobfuscation in {}ms", loadEndTime - loadStartTime);
        nameMappings = obfuscatedMappings.get();
      } else {
        LOGGER.warn("Unable to load mappings for runtime that's not using the official mappings");
        nameMappings = new NoNameMappings();
      }
    } else {
      LOGGER.info("No mappings needed for runtime that's using the official mappings");
      nameMappings = new NoNameMappings();
    }
    this.nameMappings = nameMappings;
  }

  public NameMappings get() {
    return nameMappings;
  }
}
