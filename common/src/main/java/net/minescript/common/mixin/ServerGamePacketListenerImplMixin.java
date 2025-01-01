// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minescript.common.server.EventHandlers;
import net.minescript.common.server.MinescriptServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
  @Inject(
      at = @At("HEAD"),
      method = "handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V",
      cancellable = false)
  public void handleAnimate(ServerboundSwingPacket packet, CallbackInfo ci) {
    if (!isSameThread()) {
      return;
    }
    var listener = (ServerGamePacketListenerImpl) (Object) this;
    EventHandlers.onAnimate(listener, packet);
  }

  private static boolean isSameThread() {
    var server = MinescriptServer.getMinecraftServer();
    return server != null && server.isSameThread();
  }
}
