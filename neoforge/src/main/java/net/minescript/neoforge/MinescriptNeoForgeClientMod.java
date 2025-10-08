// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.neoforge;

import static net.minescript.common.Minescript.ENTER_KEY;
import static net.minescript.common.Minescript.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minescript.common.Minescript;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinescriptNeoForgeClientMod {
  private static final Logger LOGGER = LogManager.getLogger();

  public MinescriptNeoForgeClientMod() {}

  @EventBusSubscriber(
      modid = Constants.MODID,
      bus = EventBusSubscriber.Bus.MOD,
      value = Dist.CLIENT)
  public static class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
      LOGGER.info("(minescript) Minescript mod starting...");
      Minescript.init(new NeoForgePlatform());
    }
  }

  @EventBusSubscriber(Dist.CLIENT)
  public static class ClientEvents {
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
        return;
      }
      Minescript.onRenderWorld(event);
    }

    @SubscribeEvent
    public static void onKeyboardKeyPressedEvent(ScreenEvent.KeyPressed.Pre event) {
      if (Minescript.onKeyboardKeyPressed(event.getScreen(), event.getKeyCode())) {
        event.setCanceled(true);
      }
    }

    @SubscribeEvent
    public static void onKeyInputEvent(InputEvent.Key event) {
      var key = event.getKey();
      var action = event.getAction();
      var screen = Minecraft.getInstance().screen;
      if (screen == null) {
        Minescript.onKeyInput(key);
      } else if (config != null
          && (key == ENTER_KEY || key == config.secondaryEnterKeyCode())
          && action == Constants.KEY_ACTION_DOWN
          && Minescript.onKeyboardKeyPressed(screen, key)) {
        // TODO(maxuser): InputEvent.Key isn't cancellable with NeoForge.
        // event.setCanceled(true);
      }
    }

    @SubscribeEvent
    public static void onChunkLoadEvent(ChunkEvent.Load event) {
      if (event.getLevel() instanceof ClientLevel) {
        Minescript.onChunkLoad(event.getLevel(), event.getChunk());
      }
    }

    @SubscribeEvent
    public static void onChunkUnloadEvent(ChunkEvent.Unload event) {
      if (event.getLevel() instanceof ClientLevel) {
        Minescript.onChunkUnload(event.getLevel(), event.getChunk());
      }
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Pre event) {
      if (event.getLevel().isClientSide()) {
        Minescript.onClientWorldTick();
      }
    }
  }
}
