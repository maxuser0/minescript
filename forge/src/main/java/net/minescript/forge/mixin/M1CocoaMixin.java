package net.minescript.forge.mixin;

import com.mojang.blaze3d.platform.MacosUtil;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// TODO(maxuser): Temporary fix. See https://moddingtutorials.org/o19/m1
@Mixin(MacosUtil.class)
public class M1CocoaMixin {
  @Inject(at = @At("HEAD"), method = "loadIcon", cancellable = true)
  private static void loadIcon(InputStream input, CallbackInfo cbInfo) {
    if (isAppleSlilicon()) cbInfo.cancel();
  }

  private static boolean isAppleSlilicon() {
    return System.getProperty("os.arch").equals("aarch64")
        && System.getProperty("os.name").equals("Mac OS X");
  }
}
