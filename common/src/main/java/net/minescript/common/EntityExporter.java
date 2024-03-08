// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

  public JsonArray export(Iterable<? extends Entity> entities) {
    var result = entitiesToJsonArray(entities);
    clear();
    return result;
  }

  public JsonObject export(Entity entity) {
    var result = entityToJsonObject(entity);
    clear();
    return result;
  }

  private void clear() {
    exportedEntityUuids.clear();
    entitiesToExport.clear();
  }

  private JsonArray entitiesToJsonArray(Iterable<? extends Entity> entities) {
    var jsonEntities = new JsonArray();

    // Export top-level entities.
    for (var entity : entities) {
      try {
        jsonEntities.add(entityToJsonObject(entity));
      } catch (DuplicateEntityException e) {
        LOGGER.error("Ignoring duplicate entity while exporting to JSON: {}", e.getMessage());
      }
    }

    // Export any entities that were referenced by top-level entities but for whatever reason
    // weren't listed among top-level entities. This ensures that all referenced entities are
    // themselves exported.
    for (var entity : entitiesToExport.values()) {
      try {
        jsonEntities.add(entityToJsonObject(entity));
      } catch (DuplicateEntityException e) {
        LOGGER.error("Ignoring duplicate entity while exporting to JSON: {}", e.getMessage());
      }
    }
    return jsonEntities;
  }

  private JsonObject entityToJsonObject(Entity entity) {
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
    var jsonEntity = new JsonObject();
    jsonEntity.addProperty("name", entity.getName().getString());
    jsonEntity.addProperty("type", entity.getType().toString());
    jsonEntity.addProperty("uuid", uuid);
    if (entity instanceof LivingEntity livingEntity) {
      jsonEntity.addProperty("health", livingEntity.getHealth());
    }
    if (entity == minecraft.player) {
      jsonEntity.addProperty("local", true);
    }

    var v = entity.getDeltaMovement();

    double x = entity.getX();
    double y = entity.getY();
    double z = entity.getZ();

    var position = new JsonArray();
    position.add(x);
    position.add(y);
    position.add(z);
    jsonEntity.add("position", position);

    final double epsilon = 0.0001;
    if (positionInterpolation > epsilon
        && (Math.abs(v.x) > epsilon || Math.abs(v.y) > epsilon || Math.abs(v.z) > epsilon)) {
      var lerpPosition = new JsonArray();
      lerpPosition.add(x + v.x * positionInterpolation);
      lerpPosition.add(y + v.y * positionInterpolation);
      lerpPosition.add(z + v.z * positionInterpolation);
      jsonEntity.add("lerp_position", lerpPosition);
    }

    jsonEntity.addProperty("yaw", entity.getYRot());
    jsonEntity.addProperty("pitch", entity.getXRot());

    var velocity = new JsonArray();
    velocity.add(v.x);
    velocity.add(v.y);
    velocity.add(v.z);
    jsonEntity.add("velocity", velocity);

    if (!entity.getPassengers().isEmpty()) {
      var jsonPassengers = new JsonArray();
      for (var passenger : entity.getPassengers()) {
        jsonPassengers.add(passenger.getUUID().toString());
      }
      jsonEntity.add("passengers", jsonPassengers);
    }

    if (includeNbt) {
      var nbt = new CompoundTag();
      jsonEntity.addProperty("nbt", entity.saveWithoutId(nbt).toString());
    }

    return jsonEntity;
  }
}
