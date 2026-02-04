// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import net.minecraft.client.MouseHandler;
import net.minescript.common.Minescript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
  @Shadow
  public abstract double xpos();

  @Shadow
  public abstract double ypos();

  @Inject(at = @At("HEAD"), method = "onPress(JIII)V", cancellable = false)
  private void onPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
    Minescript.onMouseClick(button, action, modifiers, this.xpos(), this.ypos());
  }
}
