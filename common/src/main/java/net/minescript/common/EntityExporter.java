// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minescript.common.dataclasses.EntityData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utility for exporting entities as JSON. */
public class EntityExporter {
  private static final Logger LOGGER = LogManager.getLogger();

  private final double positionInterpolation;
  private final boolean includeNbt;

  // UUIDs of entities that have already been exported from this EntityExporter.
  private final Set<String> exportedEntityUuids = new HashSet<>();

  // Entities that have been referenced by this EntityExporter but not yet exported. E.g. entities
  // referenced as passengers. This allows the exporter to track entities that are referenced as
  // passengers but might not be listed as top-level entities. This allows the exporter to ensure
  // that all referenced entities get their data exported.
  private final Map<String, Entity> entitiesToExport = new HashMap<>();

  private static class DuplicateEntityException extends RuntimeException {
    public DuplicateEntityException(String message) {
      super(message);
    }
  }

  /**
   * Create {@code EntityExporter}.
   *
   * @param positionInterpolation value from 0 to 1 indicating time ratio from last tick to next
   * @param includeNbt if true, export NBT data for each entity
   */
  public EntityExporter(double positionInterpolation, boolean includeNbt) {
    this.positionInterpolation = positionInterpolation;
    this.includeNbt = includeNbt;
  }

  public EntityData[] export(Iterable<? extends Entity> entities) {
    var result = exportEntities(entities);
    clear();
    return result;
  }

  public EntityData export(Entity entity) {
    var result = exportEntity(entity);
    clear();
    return result;
  }

  private void clear() {
    exportedEntityUuids.clear();
    entitiesToExport.clear();
  }

  private EntityData[] exportEntities(Iterable<? extends Entity> entities) {
    var jsonEntities = new ArrayList<EntityData>();

    // Export top-level entities.
    for (var entity : entities) {
      try {
        jsonEntities.add(exportEntity(entity));
      } catch (DuplicateEntityException e) {
        LOGGER.error("Ignoring duplicate entity while exporting to JSON: {}", e.getMessage());
      }
    }

    // Export any entities that were referenced by top-level entities but for whatever reason
    // weren't listed among top-level entities. This ensures that all referenced entities are
    // themselves exported.
    for (var entity : entitiesToExport.values()) {
      try {
        jsonEntities.add(exportEntity(entity));
      } catch (DuplicateEntityException e) {
        LOGGER.error("Ignoring duplicate entity while exporting to JSON: {}", e.getMessage());
      }
    }
    return jsonEntities.toArray(EntityData[]::new);
  }

  private EntityData exportEntity(Entity entity) {
    String uuid = entity.getUUID().toString();
    if (exportedEntityUuids.contains(uuid)) {
      throw new DuplicateEntityException(uuid);
    }
    exportedEntityUuids.add(uuid);

    if (entitiesToExport.containsKey(uuid)) {
      // This entity was previously referenced as a passenger of another entity that was exported.
      // Now that the passenger is being exported, remove this entity from entitiesToExport.
      entitiesToExport.remove(uuid);
    }

    var minecraft = Minecraft.getInstance();
    var entityData = new EntityData();
    entityData.name = entity.getName().getString();
    entityData.type = entity.getType().toString();
    entityData.uuid = uuid;
    entityData.id = entity.getId();
    if (entity instanceof LivingEntity livingEntity) {
      entityData.health = livingEntity.getHealth();
    }
    if (entity == minecraft.player) {
      entityData.local = true;
    }

    var v = entity.getDeltaMovement();

    double x = entity.getX();
    double y = entity.getY();
    double z = entity.getZ();

    entityData.position[0] = x;
    entityData.position[1] = y;
    entityData.position[2] = z;

    final double epsilon = 0.0001;
    if (positionInterpolation > epsilon
        && (Math.abs(v.x) > epsilon || Math.abs(v.y) > epsilon || Math.abs(v.z) > epsilon)) {
      entityData.lerp_position =
          new double[] {
            x + v.x * positionInterpolation,
            y + v.y * positionInterpolation,
            z + v.z * positionInterpolation
          };
    }

    entityData.yaw = entity.getYRot();
    entityData.pitch = entity.getXRot();

    entityData.velocity[0] = v.x;
    entityData.velocity[1] = v.y;
    entityData.velocity[2] = v.z;

    if (!entity.getPassengers().isEmpty()) {
      var jsonPassengers = new ArrayList<String>();
      for (var passenger : entity.getPassengers()) {
        jsonPassengers.add(passenger.getUUID().toString());
      }
      entityData.passengers = jsonPassengers.toArray(String[]::new);
    }

    if (includeNbt) {
      var nbt = new CompoundTag();
      entityData.nbt = entity.saveWithoutId(nbt).toString();
    }

    return entityData;
  }
}
