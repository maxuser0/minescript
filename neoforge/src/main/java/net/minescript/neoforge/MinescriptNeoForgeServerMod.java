// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minescript.common.server.MinescriptServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinescriptNeoForgeServerMod {
  private static final Logger LOGGER = LogManager.getLogger();

  public MinescriptNeoForgeServerMod() {}

  @EventBusSubscriber(
      modid = Constants.MODID,
      bus = EventBusSubscriber.Bus.MOD,
      value = Dist.DEDICATED_SERVER)
  public static class ServerModEvents {
    @SubscribeEvent
    public static void onServerSetup(FMLDedicatedServerSetupEvent event) {
      LOGGER.info(
          "(minescript) Minescript server mod starting with cwd: {}",
          System.getProperty("user.dir"));
      MinescriptServer.init();
    }
  }

  @EventBusSubscriber(Dist.DEDICATED_SERVER)
  public static class ServerEvents {
    @SubscribeEvent
    public static void onChunkLoadEvent(ChunkEvent.Load event) {
      if (event.getLevel() instanceof ServerLevel) {
        // TODO(maxuser): LOGGER.info("(minescript) Loading chunk: {}", event);
      }
    }

    @SubscribeEvent
    public static void onChunkUnloadEvent(ChunkEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel) {
        // TODO(maxuser): LOGGER.info("(minescript) Unloading chunk: {}", event);
      }
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Pre event) {
      if (!event.getLevel().isClientSide()) {
        MinescriptServer.onWorldTick();
      }
    }

    @SubscribeEvent
    public static void RegisterCommads(RegisterCommandsEvent event) {
      MinescriptServer.registerCommands(
          event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }
  }
}
