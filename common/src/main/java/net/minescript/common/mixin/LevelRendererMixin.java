// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minescript.common.LevelRenderContext;
import net.minescript.common.Minescript;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

  @Inject(
      at = @At("HEAD"),
      method =
          "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V")
  public void renderHead(
      GraphicsResourceAllocator graphicsResourceAllocator,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      GpuBufferSlice fogBuffer,
      Vector4f fogColor,
      boolean renderSky,
      boolean consistentDepthRequired,
      CallbackInfo ci) {
    Minescript.onRenderBegin(
        new LevelRenderContext(
            (LevelRenderer) (Object) this,
            renderBlockOutline,
            cameraState,
            fogBuffer,
            fogColor,
            renderSky,
            consistentDepthRequired));
  }

  @Inject(
      at = @At("TAIL"),
      method =
          "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V")
  public void renderTail(
      GraphicsResourceAllocator graphicsResourceAllocator,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      GpuBufferSlice fogBuffer,
      Vector4f fogColor,
      boolean renderSky,
      boolean consistentDepthRequired,
      CallbackInfo ci) {
    Minescript.onRenderEnd();
  }
}
