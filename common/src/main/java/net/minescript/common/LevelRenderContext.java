// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public record LevelRenderContext(
    MultiBufferSource.BufferSource consumers,
    LevelRenderer levelRenderer,
    DeltaTracker deltaTracker,
    boolean renderBlockOutline,
    Camera camera,
    Matrix4f positionMatrix,
    Matrix4f projectionMatrix,
    GpuBufferSlice fogBuffer,
    Vector4f fogColor,
    boolean renderSky) {}
