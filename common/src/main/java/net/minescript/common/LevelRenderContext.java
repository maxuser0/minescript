// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;

public record LevelRenderContext(
    LevelRenderer levelRenderer,
    boolean renderBlockOutline,
    CameraRenderState cameraState,
    GpuBufferSlice fogBuffer,
    Vector4f fogColor,
    boolean renderSky,
    boolean consistentDepthRequired) {}
