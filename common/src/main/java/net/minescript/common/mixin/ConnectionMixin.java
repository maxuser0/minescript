// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minescript.common.Minescript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
  @Shadow
  public abstract PacketFlow getSending();

  @Inject(
      at = @At("HEAD"),
      method =
          "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
      cancellable = true)
  public void send(
      Packet<?> packet, PacketSendListener sendListener, boolean flush, CallbackInfo ci) {
    if (getSending() == PacketFlow.SERVERBOUND) {
      if (!Minescript.onServerboundPacket(
          (Connection) (Object) this, packet, sendListener, flush)) {
        ci.cancel();
      }
    }
  }
}
