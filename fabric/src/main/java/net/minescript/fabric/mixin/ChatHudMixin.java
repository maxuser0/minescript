// SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.fabric.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minescript.common.Minescript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger("ChatHudMixin");

  @Inject(
      at = @At("HEAD"),
      method =
          "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
      cancellable = true)
  private void addMessage(
      Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
    if (Minescript.onClientChatReceived(message)) {
      ci.cancel();
    }
  }
}
