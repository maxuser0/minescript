// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Represents a set of criteria for selecting entities from the world.
 *
 * <p>This record encapsulates various filters such as UUID, name, type, position, and distance, as
 * well as sorting and limiting options. It provides a flexible way to query for entities based on a
 * combination of these attributes.
 *
 * @param uuid An optional regex pattern for the entity's UUID.
 * @param name An optional regex pattern for the entity's name.
 * @param type An optional regex pattern for the entity's type (e.g., "minecraft:creeper").
 * @param position An optional base position `[x, y, z]` for distance calculations. Defaults to the
 *     player's position.
 * @param offset An optional offset `[dx, dy, dz]` from the `position` to define a bounding box for
 *     selection.
 * @param minDistance An optional minimum distance from the `position`.
 * @param maxDistance An optional maximum distance from the `position`.
 * @param sort An optional sorting method for the results.
 * @param limit An optional limit on the number of entities to return.
 */
public record EntitySelection(
    Optional<String> uuid,
    Optional<String> name,
    Optional<String> type,
    Optional<List<Double>> position,
    Optional<List<Double>> offset,
    OptionalDouble minDistance,
    OptionalDouble maxDistance,
    Optional<SortType> sort,
    OptionalInt limit) {

  /** Describes the available sorting methods for entity selections. */
  public static enum SortType {
    /** Sort entities from nearest to furthest. */
    NEAREST,
    /** Sort entities from furthest to nearest. */
    FURTHEST,
    /** Sort entities in a pseudo-random but stable order based on their hash code. */
    RANDOM,
    /** No specific sorting is applied; the order is not guaranteed. */
    ARBITRARY
  }

  /**
   * Selects entities from a given collection that match the criteria defined by this record.
   *
   * @param entities An iterable collection of entities to filter, sort, and limit.
   * @return A new list of {@link Entity} objects that match the selection criteria.
   */
  public List<Entity> selectFrom(Iterable<? extends Entity> entities) {
    var minecraft = Minecraft.getInstance();
    var player = minecraft.player;

    Optional<Pattern> uuidPattern = uuid.map(Pattern::compile);
    Optional<Pattern> namePattern = name.map(Pattern::compile);
    Optional<Pattern> typePattern = type.map(Pattern::compile);

    OptionalDouble minDistanceSquared = optionalSquare(minDistance);
    OptionalDouble maxDistanceSquared = optionalSquare(maxDistance);

    double baseX = player.getX();
    double baseY = player.getY();
    double baseZ = player.getZ();
    if (position.isPresent()) {
      baseX = position.get().get(0);
      baseY = position.get().get(1);
      baseZ = position.get().get(2);
    }

    double offsetX = 0.;
    double offsetY = 0.;
    double offsetZ = 0.;
    if (offset.isPresent()) {
      offsetX = offset.get().get(0);
      offsetY = offset.get().get(1);
      offsetZ = offset.get().get(2);
    }

    double minX = Math.min(baseX, baseX + offsetX);
    double maxX = Math.max(baseX, baseX + offsetX);
    double minY = Math.min(baseY, baseY + offsetY);
    double maxY = Math.max(baseY, baseY + offsetY);
    double minZ = Math.min(baseZ, baseZ + offsetZ);
    double maxZ = Math.max(baseZ, baseZ + offsetZ);

    List<Entity> results = new ArrayList<>();
    for (var entity : entities) {
      double entityX = entity.getX();
      double entityY = entity.getY();
      double entityZ = entity.getZ();
      double entityDistance = -1.; // negative means unassigned

      if (minDistanceSquared.isPresent()) {
        entityDistance =
            Math3d.computeDistanceSquared(baseX, baseY, baseZ, entityX, entityY, entityZ);
        if (entityDistance < minDistanceSquared.getAsDouble()) {
          continue;
        }
      }

      if (maxDistanceSquared.isPresent()) {
        // Don't recompute entity distance if already computed above.
        if (entityDistance < 0) {
          entityDistance =
              Math3d.computeDistanceSquared(baseX, baseY, baseZ, entityX, entityY, entityZ);
        }
        if (entityDistance > maxDistanceSquared.getAsDouble()) {
          continue;
        }
      }

      if (offset.isPresent()) {
        if (entityX < minX || entityY < minY || entityZ < minZ) {
          continue;
        }
        if (entityX > maxX || entityY > maxY || entityZ > maxZ) {
          continue;
        }
      }

      if (uuidPattern.isPresent()) {
        var match = uuidPattern.get().matcher(entity.getUUID().toString());
        if (!match.matches()) {
          continue;
        }
      }

      if (namePattern.isPresent()) {
        var match = namePattern.get().matcher(entity.getName().getString());
        if (!match.matches()) {
          continue;
        }
      }

      if (typePattern.isPresent()) {
        var match = typePattern.get().matcher(entity.getType().toString());
        if (!match.matches()) {
          continue;
        }
      }

      results.add(entity);
    }

    if (sort.isPresent()) {
      switch (sort.get()) {
        case NEAREST:
          results.sort(EntityDistanceComparator.nearest(baseX, baseY, baseZ));
          break;
        case FURTHEST:
          results.sort(EntityDistanceComparator.furthest(baseX, baseY, baseZ));
          break;
        case RANDOM:
          results.sort(new EntityHashComparator());
          break;
        case ARBITRARY:
          break;
      }
    }

    if (limit.isPresent()) {
      int limitInt = limit.getAsInt();
      if (limitInt < results.size()) {
        results = results.subList(0, limitInt);
      }
    }

    return results;
  }

  private static class EntityDistanceComparator implements Comparator<Entity> {
    private final double x;
    private final double y;
    private final double z;
    private final int sign;

    /**
     * Creates a comparator that sorts entities by their distance from a point, in ascending order.
     *
     * @param x The x-coordinate of the origin point.
     * @param y The y-coordinate of the origin point.
     * @param z The z-coordinate of the origin point.
     * @return A new {@link EntityDistanceComparator} for sorting nearest first.
     */
    public static EntityDistanceComparator nearest(double x, double y, double z) {
      return new EntityDistanceComparator(x, y, z, 1);
    }

    /**
     * Creates a comparator that sorts entities by their distance from a point, in descending order.
     *
     * @param x The x-coordinate of the origin point.
     * @param y The y-coordinate of the origin point.
     * @param z The z-coordinate of the origin point.
     * @return A new {@link EntityDistanceComparator} for sorting furthest first.
     */
    public static EntityDistanceComparator furthest(double x, double y, double z) {
      return new EntityDistanceComparator(x, y, z, -1);
    }

    private EntityDistanceComparator(double x, double y, double z, int sign) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.sign = sign;
    }

    @Override
    public int compare(Entity e1, Entity e2) {
      if (e1 == e2) {
        return 0;
      }
      int comparison =
          Double.compare(
              computeEntityDistanceSquared(x, y, z, e1), computeEntityDistanceSquared(x, y, z, e2));
      if (comparison == 0) {
        return Integer.compare(e1.hashCode(), e2.hashCode());
      }
      return sign * comparison;
    }
  }

  private static class EntityHashComparator implements Comparator<Entity> {
    @Override
    public int compare(Entity e1, Entity e2) {
      if (e1 == e2) {
        return 0;
      }
      int comparison = Integer.compare(e1.hashCode(), e2.hashCode());
      if (comparison == 0) {
        return Double.compare(e1.getX(), e2.getX());
      }
      return comparison;
    }
  }

  private static double computeEntityDistanceSquared(double x, double y, double z, Entity e) {
    double x2 = e.getX();
    double y2 = e.getY();
    double z2 = e.getZ();

    double dx = x - x2;
    double dy = y - y2;
    double dz = z - z2;
    return dx * dx + dy * dy + dz * dz;
  }

  private static OptionalDouble optionalSquare(OptionalDouble od) {
    if (od.isEmpty()) {
      return od;
    } else {
      double d = od.getAsDouble();
      return OptionalDouble.of(d * d);
    }
  }
}
