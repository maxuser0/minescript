// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import static net.minescript.common.Minescript.ENTER_KEY;
import static net.minescript.common.Minescript.config;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minescript.common.Minescript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
  private static int KEY_ACTION_DOWN = 1;

  @Inject(at = @At("HEAD"), method = "keyPress(JIIII)V", cancellable = true)
  private void keyPress(
      long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
    Minescript.onKeyboardEvent(key, scanCode, action, modifiers);
    var screen = Minecraft.getInstance().screen;
    if (screen == null) {
      Minescript.onKeyInput(key);
    } else if (config != null
        && (key == ENTER_KEY || key == config.secondaryEnterKeyCode())
        && action == KEY_ACTION_DOWN
        && Minescript.onKeyboardKeyPressed(screen, key)) {
      ci.cancel();
    }
  }
}
