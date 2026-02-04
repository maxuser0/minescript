// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public record LevelRenderContext(
    MultiBufferSource.BufferSource consumers,
    LevelRenderer levelRenderer,
    DeltaTracker deltaTracker,
    boolean renderBlockOutline,
    Camera camera,
    Matrix4f positionMatrix,
    Matrix4f projectionMatrix) {}
