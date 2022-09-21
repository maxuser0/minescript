// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.forge;

import static net.minescript.common.Minescript.ENTER_KEY;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minescript.common.Minescript;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("minescript")
public class MinescriptForgeMod {
  private static final Logger LOGGER = LogManager.getLogger();

  private static int KEY_ACTION_DOWN = 1;
  private static int KEY_ACTION_REPEAT = 2;
  private static int KEY_ACTION_UP = 0;

  public MinescriptForgeMod() {
    LOGGER.info("(minescript) Minescript mod starting...");
    MinecraftForge.EVENT_BUS.register(this);

    Minescript.init();
  }

  @SubscribeEvent
  public void onKeyboardKeyPressedEvent(ScreenEvent.KeyPressed event) {
    if (Minescript.onKeyboardKeyPressed(event.getScreen(), event.getKeyCode())) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public void onKeyInputEvent(InputEvent.Key event) {
    var key = event.getKey();
    var action = event.getAction();
    var screen = Minecraft.getInstance().screen;
    if (screen == null) {
      Minescript.onKeyInput(key);
    } else if (key == ENTER_KEY
        && action == KEY_ACTION_DOWN
        && Minescript.onKeyboardKeyPressed(screen, key)) {
      event.setCanceled(true);
    }
    Minescript.onKeyInput(event.getKey());
  }

  @SubscribeEvent
  public void onChunkLoadEvent(ChunkEvent.Load event) {
    if (event.getLevel() instanceof ClientLevel) {
      Minescript.onChunkLoad(event.getLevel(), event.getChunk());
    }
  }

  @SubscribeEvent
  public void onChunkUnloadEvent(ChunkEvent.Unload event) {
    if (event.getLevel() instanceof ClientLevel) {
      Minescript.onChunkUnload(event.getLevel(), event.getChunk());
    }
  }

  @SubscribeEvent
  public void onPlayerTick(TickEvent.PlayerTickEvent event) {
    Minescript.onPlayerTick();
  }
}
