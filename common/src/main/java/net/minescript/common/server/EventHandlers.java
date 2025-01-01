// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.server;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minescript.interpreter.Script;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EventHandlers {
  private static final Logger LOGGER = LogManager.getLogger();
  private static Map<Path, Script.Function> animateHandlers = new HashMap<>();

  public static void addAnimateHandler(Path scriptPath, Script.Function eventHandler) {
    animateHandlers.put(scriptPath, eventHandler);
  }

  public static void clearJobHandlers(Path scriptPath) {
    animateHandlers.remove(scriptPath);
  }

  public static void onAnimate(
      ServerGamePacketListenerImpl listener, ServerboundSwingPacket packet) {
    for (var handler : animateHandlers.entrySet()) {
      try {
        handler.getValue().call(listener, packet);
      } catch (Exception e) {
        LOGGER.error(
            "Exception thrown from animate handler of {}: {} {}",
            handler.getKey(),
            e,
            e.getMessage());
        e.printStackTrace();
      }
    }
  }
}
