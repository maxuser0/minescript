package net.minescript.common.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minescript.common.server.MinescriptServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReentrantBlockableEventLoop.class)
public abstract class MinecraftServerMixin {
  @Inject(at = @At("TAIL"), method = "<init>(Ljava/lang/String;)V")
  public void init(String name, CallbackInfo ci) {
    var eventLoop = (Object) this;
    if (eventLoop instanceof MinecraftServer server) {
      MinescriptServer.setMinecraftServer(server);
    }
  }
}
