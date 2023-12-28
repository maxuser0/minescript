// SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.fabric.mixin;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minescript.common.Minescript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger("ChatHudMixin");

  @Inject(
      at = @At("HEAD"),
      method =
          "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
      cancellable = true)
  private void addMessage(
      Component component,
      MessageSignature messageSignature,
      GuiMessageTag guiMessageTag,
      CallbackInfo ci) {
    if (Minescript.onClientChatReceived(component)) {
      ci.cancel();
    }
  }
}
