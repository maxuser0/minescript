// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minescript.common.Minescript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FrameGraphBuilder.class)
public class FrameGraphBuilderMixin {

  private record MinescriptInspector(FrameGraphBuilder.Inspector inspector)
      implements FrameGraphBuilder.Inspector {
    @Override
    public void acquireResource(String string) {
      inspector.acquireResource(string);
    }

    @Override
    public void releaseResource(String string) {
      inspector.releaseResource(string);
    }

    @Override
    public void beforeExecutePass(String string) {
      inspector.beforeExecutePass(string);
      Minescript.onRenderPassBegin(string);
    }

    @Override
    public void afterExecutePass(String string) {
      Minescript.onRenderPassEnd(string);
      inspector.afterExecutePass(string);
    }
  }

  @ModifyVariable(
      at = @At("HEAD"),
      argsOnly = true,
      method =
          "execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V")
  private FrameGraphBuilder.Inspector wrapInspector(FrameGraphBuilder.Inspector originalInspector) {
    return new MinescriptInspector(originalInspector);
  }
}
