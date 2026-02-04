// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minescript.common.Minescript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinescriptFabricClientMod implements ClientModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger("MinescriptFabricClientMod");

  @Override
  public void onInitializeClient() {
    LOGGER.info("(minescript) Minescript mod starting...");

    ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> Minescript.onChunkLoad(world, chunk));
    ClientChunkEvents.CHUNK_UNLOAD.register(
        (world, chunk) -> Minescript.onChunkUnload(world, chunk));

    Minescript.init(new FabricPlatform());
    ClientTickEvents.START_WORLD_TICK.register(world -> Minescript.onClientWorldTick());
    ScreenEvents.AFTER_INIT.register(this::afterInitScreen);
  }

  private void afterInitScreen(Minecraft client, Screen screen, int windowWidth, int windowHeight) {
    if (screen instanceof ChatScreen) {
      ScreenKeyboardEvents.allowKeyPress(screen)
          .register(
              (_screen, key, scancode, modifiers) ->
                  !Minescript.onKeyboardKeyPressed(_screen, key));
    }
  }
}
