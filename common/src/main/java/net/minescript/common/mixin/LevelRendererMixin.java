// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minescript.common.LevelRenderContext;
import net.minescript.common.Minescript;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
  @Final @Shadow private RenderBuffers renderBuffers;

  @Inject(
      at = @At("HEAD"),
      method =
          "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")
  public void renderLevelHead(
      GraphicsResourceAllocator graphicsResourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f positionMatrix,
      Matrix4f projectionMatrix,
      CallbackInfo ci) {
    Minescript.onRenderBegin(
        new LevelRenderContext(
            renderBuffers.bufferSource(),
            (LevelRenderer) (Object) this,
            deltaTracker,
            renderBlockOutline,
            camera,
            positionMatrix,
            projectionMatrix));
  }

  @Inject(
      at = @At("TAIL"),
      method =
          "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")
  public void renderLevelTail(
      GraphicsResourceAllocator graphicsResourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f positionMatrix,
      Matrix4f projectionMatrix,
      CallbackInfo ci) {
    Minescript.onRenderEnd();
  }
}
