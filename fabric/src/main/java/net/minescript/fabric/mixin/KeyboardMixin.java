// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.fabric.mixin;

import static net.minescript.common.Minescript.ENTER_KEY;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minescript.common.Minescript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger("KeyboardMixin");

  private static int BACKSLASH_KEY = 92;

  private static int KEY_ACTION_DOWN = 1;
  private static int KEY_ACTION_REPEAT = 2;
  private static int KEY_ACTION_UP = 0;

  @Inject(at = @At("HEAD"), method = "onKey(JIIII)V", cancellable = true)
  private void keyPress(
      long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
    var screen = MinecraftClient.getInstance().currentScreen;
    if (screen == null) {
      Minescript.onKeyInput(key);
    } else if (key == ENTER_KEY
        && action == KEY_ACTION_DOWN
        && Minescript.onKeyboardKeyPressed(screen, key)) {
      ci.cancel();
    }
  }
}
