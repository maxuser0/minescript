// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minescript.common.Minescript;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
  public record Data(
      GraphicsResourceAllocator graphicsResourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f frustum,
      Matrix4f projection,
      GpuBufferSlice gpuBufferSlice,
      Vector4f clearColor,
      boolean addSkyPass) {}

  @Inject(
      at = @At("TAIL"),
      method =
          "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V")
  public void renderLevel(
      GraphicsResourceAllocator graphicsResourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f frustum,
      Matrix4f projection,
      GpuBufferSlice gpuBufferSlice,
      Vector4f clearColor,
      boolean addSkyPass,
      CallbackInfo ci) {
    Minescript.onRenderWorld(
        new Data(
            graphicsResourceAllocator,
            deltaTracker,
            renderBlockOutline,
            camera,
            frustum,
            projection,
            gpuBufferSlice,
            clearColor,
            addSkyPass));
  }
}
