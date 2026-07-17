// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.blocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a validated 3D region of blocks in the world, ready for reading.
 *
 * <p>This class ensures that the specified region is within the world's vertical limits and that
 * all necessary chunks are loaded before any read operations are performed. It provides a method to
 * iterate over all non-air blocks within its bounds.
 *
 * @param xMin The minimum x-coordinate of the region.
 * @param yMin The minimum y-coordinate of the region.
 * @param zMin The minimum z-coordinate of the region.
 * @param xMax The maximum x-coordinate of the region.
 * @param yMax The maximum y-coordinate of the region.
 * @param zMax The maximum z-coordinate of the region.
 * @param safetyLimit Whether a safety limit on the region size is enforced.
 */
public record BlockRegionReader(
    int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, boolean safetyLimit) {

  /**
   * Interface for consuming block data from {@link BlockRegionReader#readBlocks}.
   *
   * <p>Upon completion of readBlocks(), exactly one method in this interface is called for each
   * block in the region.
   */
  public interface BlockConsumer {
    /**
     * Called for each non-air block found within the region.
     *
     * @param x The world x-coordinate of the block.
     * @param y The world y-coordinate of the block.
     * @param z The world z-coordinate of the block.
     * @param block The string representation of the block's state.
     */
    void setblock(int x, int y, int z, String block);

    /**
     * Called for each air block found within the region.
     *
     * @param x The world x-coordinate of the air block.
     * @param y The world y-coordinate of the air block.
     * @param z The world z-coordinate of the air block.
     */
    void setAir(int x, int y, int z);

    /**
     * Called when a block's state cannot be converted to a string.
     *
     * @param x The world x-coordinate of the problematic block.
     * @param y The world y-coordinate of the problematic block.
     * @param z The world z-coordinate of the problematic block.
     * @param error A message describing the error.
     */
    void reportBlockError(int x, int y, int z, String error);
  }

  /**
   * Creates a {@link BlockRegionReader} for a given bounding box after performing necessary checks.
   *
   * <p>This factory method calculates the bounds from two corner points, clamps them to the world's
   * vertical limits, and verifies that all chunks within the horizontal extent of the region are
   * loaded. It can also enforce a safety limit to prevent excessively large regions from being
   * processed.
   *
   * @param x1 The x-coordinate of the first corner.
   * @param y1 The y-coordinate of the first corner.
   * @param z1 The z-coordinate of the first corner.
   * @param x2 The x-coordinate of the second corner.
   * @param y2 The y-coordinate of the second corner.
   * @param z2 The z-coordinate of the second corner.
   * @param safetyLimit If true, throws an exception if the region covers too many chunks.
   * @return A new, validated {@link BlockRegionReader} instance.
   * @throws IllegalStateException if the player is not available or if not all chunks in the region
   *     are loaded.
   * @throws IllegalArgumentException if the safety limit is enabled and exceeded.
   */
  public static BlockRegionReader withBounds(
      int x1, int y1, int z1, int x2, int y2, int z2, boolean safetyLimit) {
    var minecraft = Minecraft.getInstance();
    var player = minecraft.player;
    if (player == null) {
      throw new IllegalStateException("Unable to read blocks because player is null.");
    }

    Level level = minecraft.level;

    int xMin = Math.min(x1, x2);
    int yMin = Math.max(Math.min(y1, y2), level.getMinY());
    int zMin = Math.min(z1, z2);

    int xMax = Math.max(x1, x2);
    int yMax = Math.min(Math.max(y1, y2), level.getMaxY());
    int zMax = Math.max(z1, z2);

    if (safetyLimit) {
      // Estimate the number of chunks to check against a soft limit.
      int numChunks = ((xMax - xMin) / 16 + 1) * ((zMax - zMin) / 16 + 1);
      if (numChunks > 1600) {
        throw new IllegalArgumentException(
            "`blockpack_read_world` exceeded soft limit of 1600 chunks (region covers "
                + numChunks
                + " chunks; "
                + "override this safety check by passing `no_limit` to `copy` command or "
                + "`safety_limit=False` to `blockpack_read_world` function).");
      }
    }

    var pos = new BlockPos.MutableBlockPos();
    for (int x = xMin; x <= xMax; x += 16) {
      for (int z = zMin; z <= zMax; z += 16) {
        String block = BlockPositionReader.getBlockStateString(level, pos.set(x, 0, z));
        if (block == null || block.equals("minecraft:void_air")) {
          throw new IllegalStateException(
              "Not all chunks are loaded within the requested `copy` volume.");
        }
      }
    }

    return new BlockRegionReader(xMin, yMin, zMin, xMax, yMax, zMax, safetyLimit);
  }

  /**
   * Reads all blocks within the defined region and passes them to a consumer.
   *
   * <p>This method iterates through every block position within the reader's bounds. For each
   * block, it determines if it is air or a solid block, converts its state to a string if
   * necessary, and invokes the appropriate method on the provided {@link BlockConsumer}.
   *
   * @param blockConsumer The consumer to receive the block data.
   */
  public void readBlocks(BlockConsumer blockConsumer) {
    var minecraft = Minecraft.getInstance();
    Level level = minecraft.level;
    var pos = new BlockPos.MutableBlockPos();
    for (int y = yMin; y <= yMax; ++y) {
      for (int z = zMin; z <= zMax; ++z) {
        for (int x = xMin; x <= xMax; ++x) {
          BlockState blockState = level.getBlockState(pos.set(x, y, z));
          if (blockState.isAir()) {
            blockConsumer.setAir(x, y, z);
          } else {
            String block = BlockPositionReader.blockStateToString(blockState);
            if (block != null) {
              blockConsumer.setblock(x, y, z, block);
            } else {
              blockConsumer.reportBlockError(
                  x,
                  y,
                  z,
                  "Unexpected BlockState format at (%d, %d, %d): %s"
                      .formatted(x, y, z, blockState.toString()));
            }
          }
        }
      }
    }
  }
}
