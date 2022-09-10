// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minescript.common.Minescript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class MinescriptFabricMod implements ClientModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger("MinescriptFabricMod");

  @Override
  public void onInitializeClient() {
    LOGGER.info("(minescript) Minescript mod starting...");

    ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> Minescript.onChunkLoad(world, chunk));
    ClientChunkEvents.CHUNK_UNLOAD.register(
        (world, chunk) -> Minescript.onChunkUnload(world, chunk));

    Minescript.init();
    ClientTickEvents.START_WORLD_TICK.register(world -> Minescript.onPlayerTick());
    ScreenEvents.AFTER_INIT.register(this::afterInitScreen);
  }

  private void afterInitScreen(
      MinecraftClient client, Screen screen, int windowWidth, int windowHeight) {
    if (screen instanceof ChatScreen) {
      ScreenKeyboardEvents.allowKeyPress(screen)
          .register(
              (_screen, key, scancode, modifiers) ->
                  !Minescript.onKeyboardKeyPressed(_screen, key));
    }
  }
}
