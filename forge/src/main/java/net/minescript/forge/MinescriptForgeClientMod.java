// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.forge;

import static net.minescript.common.Minescript.ENTER_KEY;
import static net.minescript.common.Minescript.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minescript.common.Minescript;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinescriptForgeClientMod {
  private static final Logger LOGGER = LogManager.getLogger();

  public MinescriptForgeClientMod() {}

  @Mod.EventBusSubscriber(
      modid = Constants.MODID,
      bus = Mod.EventBusSubscriber.Bus.MOD,
      value = Dist.CLIENT)
  public static class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
      LOGGER.info("(minescript) Minescript mod starting...");
      Minescript.init(new ForgePlatform());
    }
  }

  @Mod.EventBusSubscriber(Dist.CLIENT)
  public static class ClientEvents {
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
        return;
      }
      Minescript.onRenderWorld();
    }

    @SubscribeEvent
    public static void onKeyboardKeyPressedEvent(ScreenEvent.KeyPressed event) {
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
      } else if ((key == ENTER_KEY || key == config.secondaryEnterKeyCode())
          && action == Constants.KEY_ACTION_DOWN
          && Minescript.onKeyboardKeyPressed(screen, key)) {
        event.setCanceled(true);
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
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
      if (event.side == LogicalSide.CLIENT && event.phase == TickEvent.Phase.START) {
        Minescript.onClientWorldTick();
      }
    }
  }
}
