// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public record LevelRenderContext(
    MultiBufferSource.BufferSource consumers,
    LevelRenderer levelRenderer,
    DeltaTracker deltaTracker,
    boolean renderBlockOutline,
    CameraRenderState cameraState,
    Matrix4fc positionMatrix,
    GpuBufferSlice fogBuffer,
    Vector4f fogColor,
    boolean renderSky,
    ChunkSectionsToRender chunkSectionsToRender) {}
