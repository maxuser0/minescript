// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minescript.common.Minescript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
  @Shadow
  public abstract String getName();

  // TODO(maxuser): Clicking the button in the Key Binds screen to reset all bindings does not
  // trigger setKey(...) below, but it seems it should since KeyBindsScreen.init() calls
  // `keyMapping.setKey(keyMapping.getDefaultKey())` for all key mappings. The workaround is to
  // restart Minecraft after clicking the "reset all" button so that Minescript has up-to-date key
  // bindings.

  @Inject(
      at = @At("TAIL"),
      method =
          "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)V")
  public void init(
      String name,
      InputConstants.Type type,
      int keyCode,
      KeyMapping.Category category,
      CallbackInfo ci) {
    Minescript.setKeyBind(name, type.getOrCreate(keyCode));
  }

  @Inject(at = @At("HEAD"), method = "setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V")
  public void setKey(InputConstants.Key key, CallbackInfo ci) {
    Minescript.setKeyBind(this.getName(), key);
  }
}
