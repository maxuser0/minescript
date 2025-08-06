// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minescript.common.Minescript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
  private static final Logger LOGGER = LoggerFactory.getLogger("ClientPacketListenerMixin");

  @Inject(
      at = @At("TAIL"),
      method = "handleAddEntity(Lnet/minecraft/network/protocol/game/ClientboundAddEntityPacket;)V",
      cancellable = false)
  private void handleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    var level = minecraft.level;
    var entity = level.getEntity(packet.getId());
    if (entity == null) {
      LOGGER.warn(
          "AddEntity event got null entity with ID {} at ({}, {}, {})",
          packet.getId(),
          packet.getX(),
          packet.getY(),
          packet.getZ());
      return;
    }
    Minescript.onAddEntityEvent(entity);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleBlockUpdate(Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;)V",
      cancellable = false)
  private void handleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    Minescript.onBlockUpdateEvent(packet.getPos(), packet.getBlockState());
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTakeItemEntity(Lnet/minecraft/network/protocol/game/ClientboundTakeItemEntityPacket;)V",
      cancellable = false)
  private void handleTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    var level = minecraft.level;
    var player = level.getEntity(packet.getPlayerId());
    if (player == null) {
      LOGGER.warn("TakeItemEntity event got null player with ID {}", packet.getPlayerId());
      return;
    }
    var item = level.getEntity(packet.getItemId());
    if (item == null) {
      LOGGER.warn("TakeItemEntity event got null item with ID {}", packet.getItemId());
      return;
    }
    Minescript.onTakeItemEvent(player, item, packet.getAmount());
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V",
      cancellable = false)
  private void handleDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    var level = minecraft.level;
    var entity = level.getEntity(packet.entityId());
    if (entity == null) {
      LOGGER.warn("Damage event got null item with ID {}", packet.entityId());
      return;
    }
    var cause = level.getEntity(packet.sourceCauseId());
    var source = packet.getSource(level);
    var sourceType = source == null ? null : source.type();
    var sourceMessage = sourceType == null ? null : sourceType.msgId();
    Minescript.onDamageEvent(entity, cause, sourceMessage);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleExplosion(Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;)V",
      cancellable = false)
  private void handleExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    // 1.21.2+ dropped support for packet.getToBlow() in ClientboundExplodePacket.
    Minescript.onExplosionEvent(packet.center().x, packet.center().y, packet.center().z, List.of());
  }

  // TODO(maxuser): How to trigger this event?
  /*
  @Inject(
      at = @At("HEAD"),
      method =
          "handleBlockDestruction(Lnet/minecraft/network/protocol/game/ClientboundBlockDestructionPacket;)V",
      cancellable = false)
  private void handleBlockDestruction(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    var level = minecraft.level;
    LOGGER.info(
        "BlockDestruction of {} at {} progress={}",
        level.getBlockState(packet.getPos()),
        packet.getId(),
        packet.getPos(),
        packet.getProgress());
  }
  */

  // TODO(maxuser): Not yet tested.
  /*
  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerCombatKill(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;)V",
      cancellable = false)
  private void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    var level = minecraft.level;
    LOGGER.info(
        "PlayerCombatKill: {} `{}`",
        level.getEntity(packet.getPlayerId()).getName().getString(),
        packet.getMessage());
  }
  */

  // Only applies to signed books, not writeable books.
  /*
  @Inject(
      at = @At("HEAD"),
      method = "handleOpenBook(Lnet/minecraft/network/protocol/game/ClientboundOpenBookPacket;)V",
      cancellable = false)
  private void handleOpenBook(ClientboundOpenBookPacket packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return;
    }
    LOGGER.info("OpenBook {}", packet.getHand());
  }
  */
}
