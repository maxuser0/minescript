// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.network.protocol.game.ClientboundDebugEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
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

  private boolean handleClientboundPacket(
      ClientPacketListener listener, Packet<?> packet, CallbackInfo ci) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.isSameThread()) {
      return false;
    }
    if (!Minescript.onClientboundPacket(listener, packet)) {
      ci.cancel();
      return false;
    }
    return true;
  }

  @Inject(
      at = @At("TAIL"),
      method = "handleAddEntity(Lnet/minecraft/network/protocol/game/ClientboundAddEntityPacket;)V",
      cancellable = false)
  private void afterHandleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
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
      cancellable = true)
  private void handleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
    if (!handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci)) {
      return;
    }
    Minescript.onBlockUpdateEvent(packet.getPos(), packet.getBlockState());
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTakeItemEntity(Lnet/minecraft/network/protocol/game/ClientboundTakeItemEntityPacket;)V",
      cancellable = true)
  private void handleTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
    if (!handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci)) {
      return;
    }
    var minecraft = Minecraft.getInstance();
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
      cancellable = true)
  private void handleDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
    if (!handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci)) {
      return;
    }
    var minecraft = Minecraft.getInstance();
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
      cancellable = true)
  private void handleExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
    if (!handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci)) {
      return;
    }
    // 1.21.2+ dropped support for packet.getToBlow() in ClientboundExplodePacket.
    Minescript.onExplosionEvent(packet.center().x, packet.center().y, packet.center().z, List.of());
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
      cancellable = true)
  private void handleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleAddEntity(Lnet/minecraft/network/protocol/game/ClientboundAddEntityPacket;)V",
      cancellable = true)
  private void handleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetEntityMotion(Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;)V",
      cancellable = true)
  private void handleSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetEntityData(Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;)V",
      cancellable = true)
  private void handleSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleEntityPositionSync(Lnet/minecraft/network/protocol/game/ClientboundEntityPositionSyncPacket;)V",
      cancellable = true)
  private void handleEntityPositionSync(
      ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTeleportEntity(Lnet/minecraft/network/protocol/game/ClientboundTeleportEntityPacket;)V",
      cancellable = true)
  private void handleTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTickingState(Lnet/minecraft/network/protocol/game/ClientboundTickingStatePacket;)V",
      cancellable = true)
  private void handleTickingState(ClientboundTickingStatePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTickingStep(Lnet/minecraft/network/protocol/game/ClientboundTickingStepPacket;)V",
      cancellable = true)
  private void handleTickingStep(ClientboundTickingStepPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetHeldSlot(Lnet/minecraft/network/protocol/game/ClientboundSetHeldSlotPacket;)V",
      cancellable = true)
  private void handleSetHeldSlot(ClientboundSetHeldSlotPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMoveEntity(Lnet/minecraft/network/protocol/game/ClientboundMoveEntityPacket;)V",
      cancellable = true)
  private void handleMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMinecartAlongTrack(Lnet/minecraft/network/protocol/game/ClientboundMoveMinecartPacket;)V",
      cancellable = true)
  private void handleMinecartAlongTrack(ClientboundMoveMinecartPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRotateMob(Lnet/minecraft/network/protocol/game/ClientboundRotateHeadPacket;)V",
      cancellable = true)
  private void handleRotateMob(ClientboundRotateHeadPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRemoveEntities(Lnet/minecraft/network/protocol/game/ClientboundRemoveEntitiesPacket;)V",
      cancellable = true)
  private void handleRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
      cancellable = true)
  private void handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRotatePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerRotationPacket;)V",
      cancellable = true)
  private void handleRotatePlayer(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleChunkBlocksUpdate(Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;)V",
      cancellable = true)
  private void handleChunkBlocksUpdate(
      ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleLevelChunkWithLight(Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;)V",
      cancellable = true)
  private void handleLevelChunkWithLight(
      ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleChunksBiomes(Lnet/minecraft/network/protocol/game/ClientboundChunksBiomesPacket;)V",
      cancellable = true)
  private void handleChunksBiomes(ClientboundChunksBiomesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleForgetLevelChunk(Lnet/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket;)V",
      cancellable = true)
  private void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V",
      cancellable = true)
  private void handleConfigurationStart(
      ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V",
      cancellable = true)
  private void handleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerChat(Lnet/minecraft/network/protocol/game/ClientboundPlayerChatPacket;)V",
      cancellable = true)
  private void handlePlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDisguisedChat(Lnet/minecraft/network/protocol/game/ClientboundDisguisedChatPacket;)V",
      cancellable = true)
  private void handleDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDeleteChat(Lnet/minecraft/network/protocol/game/ClientboundDeleteChatPacket;)V",
      cancellable = true)
  private void handleDeleteChat(ClientboundDeleteChatPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleAnimate(Lnet/minecraft/network/protocol/game/ClientboundAnimatePacket;)V",
      cancellable = true)
  private void handleAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleHurtAnimation(Lnet/minecraft/network/protocol/game/ClientboundHurtAnimationPacket;)V",
      cancellable = true)
  private void handleHurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleSetTime(Lnet/minecraft/network/protocol/game/ClientboundSetTimePacket;)V",
      cancellable = true)
  private void handleSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetSpawn(Lnet/minecraft/network/protocol/game/ClientboundSetDefaultSpawnPositionPacket;)V",
      cancellable = true)
  private void handleSetSpawn(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetEntityPassengersPacket(Lnet/minecraft/network/protocol/game/ClientboundSetPassengersPacket;)V",
      cancellable = true)
  private void handleSetEntityPassengersPacket(
      ClientboundSetPassengersPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleEntityLinkPacket(Lnet/minecraft/network/protocol/game/ClientboundSetEntityLinkPacket;)V",
      cancellable = true)
  private void handleEntityLinkPacket(ClientboundSetEntityLinkPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;)V",
      cancellable = true)
  private void handleEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleSetHealth(Lnet/minecraft/network/protocol/game/ClientboundSetHealthPacket;)V",
      cancellable = true)
  private void handleSetHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetExperience(Lnet/minecraft/network/protocol/game/ClientboundSetExperiencePacket;)V",
      cancellable = true)
  private void handleSetExperience(ClientboundSetExperiencePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
      cancellable = true)
  private void handleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMountScreenOpen(Lnet/minecraft/network/protocol/game/ClientboundMountScreenOpenPacket;)V",
      cancellable = true)
  private void handleMountScreenOpen(ClientboundMountScreenOpenPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V",
      cancellable = true)
  private void handleOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V",
      cancellable = true)
  private void handleContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetCursorItem(Lnet/minecraft/network/protocol/game/ClientboundSetCursorItemPacket;)V",
      cancellable = true)
  private void handleSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetPlayerInventory(Lnet/minecraft/network/protocol/game/ClientboundSetPlayerInventoryPacket;)V",
      cancellable = true)
  private void handleSetPlayerInventory(
      ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleContainerContent(Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;)V",
      cancellable = true)
  private void handleContainerContent(
      ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleOpenSignEditor(Lnet/minecraft/network/protocol/game/ClientboundOpenSignEditorPacket;)V",
      cancellable = true)
  private void handleOpenSignEditor(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleBlockEntityData(Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;)V",
      cancellable = true)
  private void handleBlockEntityData(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleContainerSetData(Lnet/minecraft/network/protocol/game/ClientboundContainerSetDataPacket;)V",
      cancellable = true)
  private void handleContainerSetData(ClientboundContainerSetDataPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetEquipment(Lnet/minecraft/network/protocol/game/ClientboundSetEquipmentPacket;)V",
      cancellable = true)
  private void handleSetEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleContainerClose(Lnet/minecraft/network/protocol/game/ClientboundContainerClosePacket;)V",
      cancellable = true)
  private void handleContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleBlockEvent(Lnet/minecraft/network/protocol/game/ClientboundBlockEventPacket;)V",
      cancellable = true)
  private void handleBlockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleBlockDestruction(Lnet/minecraft/network/protocol/game/ClientboundBlockDestructionPacket;)V",
      cancellable = true)
  private void handleBlockDestruction(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleGameEvent(Lnet/minecraft/network/protocol/game/ClientboundGameEventPacket;)V",
      cancellable = true)
  private void handleGameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMapItemData(Lnet/minecraft/network/protocol/game/ClientboundMapItemDataPacket;)V",
      cancellable = true)
  private void handleMapItemData(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleLevelEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelEventPacket;)V",
      cancellable = true)
  private void handleLevelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleUpdateAdvancementsPacket(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V",
      cancellable = true)
  private void handleUpdateAdvancementsPacket(
      ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSelectAdvancementsTab(Lnet/minecraft/network/protocol/game/ClientboundSelectAdvancementsTabPacket;)V",
      cancellable = true)
  private void handleSelectAdvancementsTab(
      ClientboundSelectAdvancementsTabPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleCommands(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V",
      cancellable = true)
  private void handleCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleStopSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundStopSoundPacket;)V",
      cancellable = true)
  private void handleStopSoundEvent(ClientboundStopSoundPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleCommandSuggestions(Lnet/minecraft/network/protocol/game/ClientboundCommandSuggestionsPacket;)V",
      cancellable = true)
  private void handleCommandSuggestions(
      ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleUpdateRecipes(Lnet/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket;)V",
      cancellable = true)
  private void handleUpdateRecipes(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleLookAt(Lnet/minecraft/network/protocol/game/ClientboundPlayerLookAtPacket;)V",
      cancellable = true)
  private void handleLookAt(ClientboundPlayerLookAtPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTagQueryPacket(Lnet/minecraft/network/protocol/game/ClientboundTagQueryPacket;)V",
      cancellable = true)
  private void handleTagQueryPacket(ClientboundTagQueryPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleAwardStats(Lnet/minecraft/network/protocol/game/ClientboundAwardStatsPacket;)V",
      cancellable = true)
  private void handleAwardStats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRecipeBookAdd(Lnet/minecraft/network/protocol/game/ClientboundRecipeBookAddPacket;)V",
      cancellable = true)
  private void handleRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRecipeBookRemove(Lnet/minecraft/network/protocol/game/ClientboundRecipeBookRemovePacket;)V",
      cancellable = true)
  private void handleRecipeBookRemove(ClientboundRecipeBookRemovePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRecipeBookSettings(Lnet/minecraft/network/protocol/game/ClientboundRecipeBookSettingsPacket;)V",
      cancellable = true)
  private void handleRecipeBookSettings(
      ClientboundRecipeBookSettingsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleUpdateMobEffect(Lnet/minecraft/network/protocol/game/ClientboundUpdateMobEffectPacket;)V",
      cancellable = true)
  private void handleUpdateMobEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleUpdateTags(Lnet/minecraft/network/protocol/common/ClientboundUpdateTagsPacket;)V",
      cancellable = true)
  private void handleUpdateTags(ClientboundUpdateTagsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerCombatEnd(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatEndPacket;)V",
      cancellable = true)
  private void handlePlayerCombatEnd(ClientboundPlayerCombatEndPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerCombatEnter(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatEnterPacket;)V",
      cancellable = true)
  private void handlePlayerCombatEnter(ClientboundPlayerCombatEnterPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerCombatKill(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;)V",
      cancellable = true)
  private void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleChangeDifficulty(Lnet/minecraft/network/protocol/game/ClientboundChangeDifficultyPacket;)V",
      cancellable = true)
  private void handleChangeDifficulty(ClientboundChangeDifficultyPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleSetCamera(Lnet/minecraft/network/protocol/game/ClientboundSetCameraPacket;)V",
      cancellable = true)
  private void handleSetCamera(ClientboundSetCameraPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleInitializeBorder(Lnet/minecraft/network/protocol/game/ClientboundInitializeBorderPacket;)V",
      cancellable = true)
  private void handleInitializeBorder(ClientboundInitializeBorderPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetBorderCenter(Lnet/minecraft/network/protocol/game/ClientboundSetBorderCenterPacket;)V",
      cancellable = true)
  private void handleSetBorderCenter(ClientboundSetBorderCenterPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetBorderLerpSize(Lnet/minecraft/network/protocol/game/ClientboundSetBorderLerpSizePacket;)V",
      cancellable = true)
  private void handleSetBorderLerpSize(ClientboundSetBorderLerpSizePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetBorderSize(Lnet/minecraft/network/protocol/game/ClientboundSetBorderSizePacket;)V",
      cancellable = true)
  private void handleSetBorderSize(ClientboundSetBorderSizePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetBorderWarningDistance(Lnet/minecraft/network/protocol/game/ClientboundSetBorderWarningDistancePacket;)V",
      cancellable = true)
  private void handleSetBorderWarningDistance(
      ClientboundSetBorderWarningDistancePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetBorderWarningDelay(Lnet/minecraft/network/protocol/game/ClientboundSetBorderWarningDelayPacket;)V",
      cancellable = true)
  private void handleSetBorderWarningDelay(
      ClientboundSetBorderWarningDelayPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTitlesClear(Lnet/minecraft/network/protocol/game/ClientboundClearTitlesPacket;)V",
      cancellable = true)
  private void handleTitlesClear(ClientboundClearTitlesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleServerData(Lnet/minecraft/network/protocol/game/ClientboundServerDataPacket;)V",
      cancellable = true)
  private void handleServerData(ClientboundServerDataPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleCustomChatCompletions(Lnet/minecraft/network/protocol/game/ClientboundCustomChatCompletionsPacket;)V",
      cancellable = true)
  private void handleCustomChatCompletions(
      ClientboundCustomChatCompletionsPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTabListCustomisation(Lnet/minecraft/network/protocol/game/ClientboundTabListPacket;)V",
      cancellable = true)
  private void handleTabListCustomisation(ClientboundTabListPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleRemoveMobEffect(Lnet/minecraft/network/protocol/game/ClientboundRemoveMobEffectPacket;)V",
      cancellable = true)
  private void handleRemoveMobEffect(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerInfoRemove(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoRemovePacket;)V",
      cancellable = true)
  private void handlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerInfoUpdate(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;)V",
      cancellable = true)
  private void handlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlayerAbilities(Lnet/minecraft/network/protocol/game/ClientboundPlayerAbilitiesPacket;)V",
      cancellable = true)
  private void handlePlayerAbilities(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V",
      cancellable = true)
  private void handleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSoundEntityEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundEntityPacket;)V",
      cancellable = true)
  private void handleSoundEntityEvent(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleBossUpdate(Lnet/minecraft/network/protocol/game/ClientboundBossEventPacket;)V",
      cancellable = true)
  private void handleBossUpdate(ClientboundBossEventPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleItemCooldown(Lnet/minecraft/network/protocol/game/ClientboundCooldownPacket;)V",
      cancellable = true)
  private void handleItemCooldown(ClientboundCooldownPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ClientboundMoveVehiclePacket;)V",
      cancellable = true)
  private void handleMoveVehicle(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleOpenBook(Lnet/minecraft/network/protocol/game/ClientboundOpenBookPacket;)V",
      cancellable = true)
  private void handleOpenBook(ClientboundOpenBookPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleAddObjective(Lnet/minecraft/network/protocol/game/ClientboundSetObjectivePacket;)V",
      cancellable = true)
  private void handleAddObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleSetScore(Lnet/minecraft/network/protocol/game/ClientboundSetScorePacket;)V",
      cancellable = true)
  private void handleSetScore(ClientboundSetScorePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleResetScore(Lnet/minecraft/network/protocol/game/ClientboundResetScorePacket;)V",
      cancellable = true)
  private void handleResetScore(ClientboundResetScorePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetDisplayObjective(Lnet/minecraft/network/protocol/game/ClientboundSetDisplayObjectivePacket;)V",
      cancellable = true)
  private void handleSetDisplayObjective(
      ClientboundSetDisplayObjectivePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetPlayerTeamPacket(Lnet/minecraft/network/protocol/game/ClientboundSetPlayerTeamPacket;)V",
      cancellable = true)
  private void handleSetPlayerTeamPacket(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V",
      cancellable = true)
  private void handleParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleUpdateAttributes(Lnet/minecraft/network/protocol/game/ClientboundUpdateAttributesPacket;)V",
      cancellable = true)
  private void handleUpdateAttributes(ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePlaceRecipe(Lnet/minecraft/network/protocol/game/ClientboundPlaceGhostRecipePacket;)V",
      cancellable = true)
  private void handlePlaceRecipe(ClientboundPlaceGhostRecipePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleLightUpdatePacket(Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacket;)V",
      cancellable = true)
  private void handleLightUpdatePacket(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleMerchantOffers(Lnet/minecraft/network/protocol/game/ClientboundMerchantOffersPacket;)V",
      cancellable = true)
  private void handleMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetChunkCacheRadius(Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheRadiusPacket;)V",
      cancellable = true)
  private void handleSetChunkCacheRadius(
      ClientboundSetChunkCacheRadiusPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetSimulationDistance(Lnet/minecraft/network/protocol/game/ClientboundSetSimulationDistancePacket;)V",
      cancellable = true)
  private void handleSetSimulationDistance(
      ClientboundSetSimulationDistancePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleSetChunkCacheCenter(Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheCenterPacket;)V",
      cancellable = true)
  private void handleSetChunkCacheCenter(
      ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleBlockChangedAck(Lnet/minecraft/network/protocol/game/ClientboundBlockChangedAckPacket;)V",
      cancellable = true)
  private void handleBlockChangedAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method = "handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",
      cancellable = true)
  private void handleBundlePacket(ClientboundBundlePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleProjectilePowerPacket(Lnet/minecraft/network/protocol/game/ClientboundProjectilePowerPacket;)V",
      cancellable = true)
  private void handleProjectilePowerPacket(
      ClientboundProjectilePowerPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleChunkBatchStart(Lnet/minecraft/network/protocol/game/ClientboundChunkBatchStartPacket;)V",
      cancellable = true)
  private void handleChunkBatchStart(ClientboundChunkBatchStartPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleChunkBatchFinished(Lnet/minecraft/network/protocol/game/ClientboundChunkBatchFinishedPacket;)V",
      cancellable = true)
  private void handleChunkBatchFinished(
      ClientboundChunkBatchFinishedPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDebugSample(Lnet/minecraft/network/protocol/game/ClientboundDebugSamplePacket;)V",
      cancellable = true)
  private void handleDebugSample(ClientboundDebugSamplePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handlePongResponse(Lnet/minecraft/network/protocol/ping/ClientboundPongResponsePacket;)V",
      cancellable = true)
  private void handlePongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleTestInstanceBlockStatus(Lnet/minecraft/network/protocol/game/ClientboundTestInstanceBlockStatus;)V",
      cancellable = true)
  private void handleTestInstanceBlockStatus(
      ClientboundTestInstanceBlockStatus packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleWaypoint(Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;)V",
      cancellable = true)
  private void handleWaypoint(ClientboundTrackedWaypointPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDebugChunkValue(Lnet/minecraft/network/protocol/game/ClientboundDebugChunkValuePacket;)V",
      cancellable = true)
  private void handleDebugChunkValue(ClientboundDebugChunkValuePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDebugBlockValue(Lnet/minecraft/network/protocol/game/ClientboundDebugBlockValuePacket;)V",
      cancellable = true)
  private void handleDebugBlockValue(ClientboundDebugBlockValuePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDebugEntityValue(Lnet/minecraft/network/protocol/game/ClientboundDebugEntityValuePacket;)V",
      cancellable = true)
  private void handleDebugEntityValue(ClientboundDebugEntityValuePacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleDebugEvent(Lnet/minecraft/network/protocol/game/ClientboundDebugEventPacket;)V",
      cancellable = true)
  private void handleDebugEvent(ClientboundDebugEventPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }

  @Inject(
      at = @At("HEAD"),
      method =
          "handleGameTestHighlightPos(Lnet/minecraft/network/protocol/game/ClientboundGameTestHighlightPosPacket;)V",
      cancellable = true)
  private void handleGameTestHighlightPos(
      ClientboundGameTestHighlightPosPacket packet, CallbackInfo ci) {
    handleClientboundPacket((ClientPacketListener) (Object) this, packet, ci);
  }
}
