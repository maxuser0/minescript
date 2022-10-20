// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code BlockPacker} manages blocks and packs them into a {@code BlockPack}.
 *
 * <p>While {@code BlockPacker} manages a dynamic set of blocks, {@code BlockPack} is immutable.
 */
public class BlockPacker {
  private static final int X_BUILD_MIN = Integer.MIN_VALUE;
  private static final int X_BUILD_MAX = Integer.MAX_VALUE;
  private static final int Y_BUILD_MIN = -64;
  private static final int Y_BUILD_MAX = 319;
  private static final int Z_BUILD_MIN = Integer.MIN_VALUE;
  private static final int Z_BUILD_MAX = Integer.MAX_VALUE;

  private final Map<Long, Tile> tiles = new TreeMap<>();
  private final int xTileSize;
  private final int yTileSize;
  private final int zTileSize;

  private int minX = X_BUILD_MAX;
  private int minY = Y_BUILD_MAX;
  private int minZ = Z_BUILD_MAX;

  public BlockPacker(int xTileSize, int yTileSize, int zTileSize) {
    this.xTileSize = xTileSize;
    this.yTileSize = yTileSize;
    this.zTileSize = zTileSize;
  }

  public void addBlock(int x, int y, int z, String blockType) {
    long key = getTileKey(x, y, z);
    Tile tile =
        tiles.computeIfAbsent(
            key,
            k -> {
              // Adjust x, y, z to non-negative values so they're amenable to bit operations.
              int adjustedX = x - X_BUILD_MIN;
              int adjustedY = y - Y_BUILD_MIN;
              int adjustedZ = z - Z_BUILD_MIN;

              int xTile =
                  (adjustedX >= 0) ? (adjustedX / xTileSize) : (((adjustedX + 1) / xTileSize) - 1);
              int yTile =
                  (adjustedY >= 0) ? (adjustedY / yTileSize) : (((adjustedY + 1) / yTileSize) - 1);
              int zTile =
                  (adjustedZ >= 0) ? (adjustedZ / zTileSize) : (((adjustedZ + 1) / zTileSize) - 1);

              int xOffset = xTile * xTileSize + X_BUILD_MIN;
              int yOffset = yTile * yTileSize + Y_BUILD_MIN;
              int zOffset = zTile * zTileSize + Z_BUILD_MIN;

              return new Tile(xOffset, yOffset, zOffset, xTileSize, yTileSize, zTileSize);
            });
    tile.addBlock(x, y, z, blockType);
    if (x < minX) {
      minX = x;
    }
    if (y < minY) {
      minY = y;
    }
    if (z < minZ) {
      minZ = z;
    }
  }

  private long getTileKey(int x, int y, int z) {
    // Adjust x, y, z to non-negative values so they're amenable to bit operations.
    int adjustedX = x - X_BUILD_MIN;
    int adjustedY = y - Y_BUILD_MIN;
    int adjustedZ = z - Z_BUILD_MIN;

    int xTile = (adjustedX >= 0) ? (adjustedX / xTileSize) : (((adjustedX + 1) / xTileSize) - 1);
    int yTile = (adjustedY >= 0) ? (adjustedY / yTileSize) : (((adjustedY + 1) / yTileSize) - 1);
    int zTile = (adjustedZ >= 0) ? (adjustedZ / zTileSize) : (((adjustedZ + 1) / zTileSize) - 1);

    // The y tile gets the high 10 bits, x tile gets the next 27 bits, and z tile the low 27 bits.
    return ((long) (yTile & 0x3ff) << 54)
        | ((long) (xTile & 0x7ffffff) << 27)
        | ((long) zTile & 0x7ffffff);
  }

  public void printDebugInfo(boolean offsetToOrigin) {
    for (Tile tile : tiles.values()) {
      int x = tile.xOffset;
      int y = tile.yOffset;
      int z = tile.zOffset;
      if (offsetToOrigin) {
        x -= minX;
        y -= minY;
        z -= minZ;
      }
      System.out.printf("# tile offset: %d %d %d\n", x, y, z);
      System.out.print(tile.getDebugInfo());
    }
  }

  // TODO(maxuser): Allow re-packing of blocks from the same BlockPacker after adding more blocks.
  public BlockPack pack() {
    // TODO(mxuser): Consider transforming tile.types into a symbol table attached to entire
    // BlockPack rather than each BlockPack.Tile.
    var packedTiles = new TreeMap<Long, BlockPack.Tile>();
    for (var entry : tiles.entrySet()) {
      long key = entry.getKey();
      var tile = entry.getValue();

      tile.computeRunLengths();
      tile.updateTypeList();
      var packedTile = new BlockPack.Tile(tile.types, tile.xOffset, tile.yOffset, tile.zOffset);
      tile.computeBlockCommands(packedTile);
      packedTiles.put(key, packedTile);
    }
    return new BlockPack(minX, minY, minZ, packedTiles);
  }

  public static class Tile {
    private final int xOffset;
    private final int yOffset;
    private final int zOffset;
    private final int xSize;
    private final int ySize;
    private final int zSize;
    private final int xzArea;
    private final int xyzVolume;
    private Map<String, Integer> typeMap = new HashMap<>();
    private Map<Integer, Integer> typeFrequencies = new HashMap<>();
    private String[] types;
    private int numBlocks = 0;
    private int nextTypeId = 0;
    private int maxRunProduct = 0;
    private int maxFrequency = 0;
    private int maxFrequencyType = -1;
    private boolean prefillVolume = false; // TODO(maxuser): allow this to be enabled
    private static final int STRUCTURE_VOID_BLOCK = 0;

    // Each value in blocks stores data in these bits:
    // - bits 0-14: block type value from typeMap (15 bits = 32768 unique values)
    // - bits 15-19: consecutive blocks of same type in +x direction
    // - bits 20-24: consecutive blocks of same type in +y direction
    // - bits 25-29: consecutive blocks of same type in +z direction
    // - bit 30: 1 if this block has been filled, 0 otherwise
    private final int[] blocks;

    public Tile(int xOffset, int yOffset, int zOffset) {
      this(xOffset, yOffset, zOffset, 16, 16, 16);
    }

    public Tile(int xOffset, int yOffset, int zOffset, int xSize, int ySize, int zSize) {
      if (xSize < 1 || xSize > 32) {
        throw new IllegalArgumentException("xSize outside bounds [1, 32]: " + xSize);
      }
      if (ySize < 1 || ySize > 32) {
        throw new IllegalArgumentException("ySize outside bounds [1, 32]: " + ySize);
      }
      if (zSize < 1 || zSize > 32) {
        throw new IllegalArgumentException("zSize outside bounds [1, 32]: " + zSize);
      }

      this.xOffset = xOffset;
      this.yOffset = yOffset;
      this.zOffset = zOffset;

      this.xSize = xSize;
      this.ySize = ySize;
      this.zSize = zSize;

      this.xzArea = xSize * zSize;
      this.xyzVolume = xzArea * ySize;

      blocks = new int[xyzVolume];
      typeId("structure_void");
    }

    public int coordToIndex(int x, int y, int z) {
      return x + xSize * z + xzArea * y;
    }

    private void indexToCoordArray(int index, int[] coord) {
      coord[0] = index % xSize;
      coord[2] = (index / xSize) % zSize;
      coord[1] = index / xzArea;
    }

    private String indexToCoordString(int index) {
      int[] coord = new int[3];
      indexToCoordArray(index, coord);
      return String.format("(%d, %d, %d)", coord[0], coord[1], coord[2]);
    }

    private int typeId(String type) {
      return typeMap.computeIfAbsent(type, k -> nextTypeId++);
    }

    private void updateTypeList() {
      if (types != null && types.length == typeMap.size()) {
        return;
      }
      types = new String[typeMap.size()];
      for (var keyValue : typeMap.entrySet()) {
        types[keyValue.getValue()] = keyValue.getKey();
      }
    }

    public void addBlock(int x, int y, int z, String blockType) {
      if (x < xOffset
          || x >= xOffset + xSize
          || y < yOffset
          || y >= yOffset + ySize
          || z < zOffset
          || z >= zOffset + zSize) {
        throw new ArrayIndexOutOfBoundsException(
            String.format(
                "Coord (%d, %d, %d) out of bounds for volume [%d-%d, %d-%d, %d-%d]",
                x,
                y,
                z,
                xOffset,
                xOffset + xSize,
                yOffset,
                yOffset + ySize,
                zOffset,
                zOffset + zSize));
      }
      int type = typeId(blockType);
      int frequency = typeFrequencies.computeIfAbsent(type, k -> 0) + 1;
      typeFrequencies.put(type, frequency);
      if (type != STRUCTURE_VOID_BLOCK && frequency > maxFrequency) {
        maxFrequencyType = type;
        maxFrequency = frequency;
      }
      ++numBlocks;
      setBlockType(x - xOffset, y - yOffset, z - zOffset, type);
    }

    public void computeRunLengths() {
      // Algorithm:
      // 1. Iterate x, y, z from 15 to 0 to accumulate run lengths in positive x, y, z direction.
      // 2. Iterate by y slice from 0 to 15
      //    - For each unfilled x, z block from (0, y, 0) to (15, y, 15):
      //      - Iterate the +x span to find the max (x, z) box from x, y, z to x', y', z'.
      //      - Iterate the +z span to find the max (x, z) box from x, y, z to x', y', z'.
      //      - Take the larger of the volumes computed in the previous two steps. If the volume is
      // at
      //        least 2 blocks, record a "fill" command; otherwise, record "setblock" commands.
      //      - Mark the blocks in the corresponding volume as "filled".

      int numVoidBlocks = xyzVolume - numBlocks;
      if (numVoidBlocks > maxFrequency) {
        maxFrequency = numVoidBlocks;
        maxFrequencyType = STRUCTURE_VOID_BLOCK;
      }

      // Compute the runs of consecutive blocks of the same type in each dimension: x, y, and z.
      for (int y = ySize - 1; y >= 0; --y) {
        for (int z = zSize - 1; z >= 0; --z) {
          for (int x = xSize - 1; x >= 0; --x) {
            int index = coordToIndex(x, y, z);
            int type = getBlockType(index);
            if (prefillVolume && type == maxFrequencyType) {
              continue;
            }

            int xPlusOneIndex = coordToIndex(x + 1, y, z);
            int yPlusOneIndex = coordToIndex(x, y + 1, z);
            int zPlusOneIndex = coordToIndex(x, y, z + 1);

            int plusXRun =
                x < xSize - 1 && getBlockType(xPlusOneIndex) == type
                    ? 1 + getBlockPlusXRun(xPlusOneIndex)
                    : 0;
            int plusYRun =
                y < ySize - 1 && getBlockType(yPlusOneIndex) == type
                    ? 1 + getBlockPlusYRun(yPlusOneIndex)
                    : 0;
            int plusZRun =
                z < zSize - 1 && getBlockType(zPlusOneIndex) == type
                    ? 1 + getBlockPlusZRun(zPlusOneIndex)
                    : 0;

            setBlockRuns(index, plusXRun, plusYRun, plusZRun);
          }
        }
      }
    }

    public void computeBlockCommands(BlockPack.Tile tile) {
      // Use the runs of consecutive blocks of the same type computed above to identify x,z-plane
      // areas and x,y,z rectangular volumes of the same type of block.
      for (int y = 0; y < ySize; ++y) {
        for (int x = 0; x < xSize; ++x) {
          for (int z = 0; z < zSize; ++z) {
            int index = coordToIndex(x, y, z);
            if (isBlockFilled(index)) {
              continue;
            }

            final int blockType = getBlockType(index);
            if (blockType == STRUCTURE_VOID_BLOCK) {
              continue;
            }

            if (prefillVolume && getBlockType(index) == maxFrequencyType) {
              continue;
            }

            int minXRun = xSize;
            int minZRun = zSize;
            int maxArea = 0;
            int maxAreaX = -1;
            int maxAreaZ = -1;
            int minYRun = Integer.MAX_VALUE;

            { // Compute max x,z rectangular area.
              final int xRun = getBlockPlusXRun(index);
              for (int x2 = x; x2 < x + xRun + 1; ++x2) {
                final int zRun = getBlockPlusZRun(coordToIndex(x2, y, z));
                if (zRun < minZRun) {
                  minZRun = zRun;
                }
                for (int z2 = z; z2 < z + minZRun + 1; ++z2) {
                  int area = (x2 - x + 1) * (z2 - z + 1);
                  if (area > maxArea) {
                    maxArea = area;
                    maxAreaX = x2;
                    maxAreaZ = z2;
                  }
                }
              }
            }

            // Compute min y run within the x,z rectangular area.
            if (maxAreaX != -1 && maxAreaZ != -1) {
              for (int x2 = x; x2 < maxAreaX + 1; ++x2) {
                for (int z2 = z; z2 < maxAreaZ + 1; ++z2) {
                  int yRun = getBlockPlusYRun(coordToIndex(x2, y, z2));
                  if (yRun < minYRun) {
                    minYRun = yRun;
                  }
                }
              }
            }

            if (minYRun < Integer.MAX_VALUE) {
              final int dx = maxAreaX - x;
              final int dy = minYRun;
              final int dz = maxAreaZ - z;
              if (dx == 0 && dy == 0 && dz == 0) {
                tile.setblocks.add(x).add(y).add(z).add(blockType);
              } else {
                tile.fills
                    .add(x)
                    .add(y)
                    .add(z)
                    .add(maxAreaX)
                    .add(y + minYRun)
                    .add(maxAreaZ)
                    .add(blockType);
              }
              for (int x2 = x; x2 < maxAreaX + 1; ++x2) {
                for (int z2 = z; z2 < maxAreaZ + 1; ++z2) {
                  for (int y2 = y; y2 < y + minYRun + 1; ++y2) {
                    setBlockFilled(x2, y2, z2);
                  }
                }
              }
            }
          }
        }
      }
    }

    private void setBlockType(int x, int y, int z, int type) {
      int index = coordToIndex(x, y, z);
      if (blocks[index] != 0) {
        updateTypeList();
        throw new IllegalStateException(
            String.format("Block at %s already set: %s", indexToCoordString(index), types[type]));
      }
      blocks[index] = type;
    }

    private void setBlockFilled(int x, int y, int z) {
      int index = coordToIndex(x, y, z);
      blocks[index] |= 0x40000000; // 1 << 30
    }

    private void setBlockRuns(int index, int plusXRun, int plusYRun, int plusZRun) {
      if (plusXRun < 0 || plusXRun >= xSize) {
        throw new IllegalArgumentException(
            String.format("+x run length not in range [0, %d): %d", xSize, plusXRun));
      }
      if (plusYRun < 0 || plusYRun >= ySize) {
        throw new IllegalArgumentException(
            String.format("+y run length not in range [0, %d): %d", ySize, plusYRun));
      }
      if (plusZRun < 0 || plusZRun >= zSize) {
        throw new IllegalArgumentException(
            String.format("+z run length not in range [0, %d): %d", zSize, plusZRun));
      }
      int value = blocks[index];

      // 0xc0007fff in binary: 11000000000000000111111111111111
      //                         ^^^^^^^^^^^^^^^
      //                       15 bits of x,y,z runs (5 bits each)
      value = (value & 0xc0007fff) | (plusXRun << 15) | (plusYRun << 20) | (plusZRun << 25);
      blocks[index] = value;
    }

    private int getBlockType(int index) {
      return blocks[index] & 0x7fff;
    }

    private int getBlockPlusXRun(int index) {
      return (blocks[index] & 0xf8000) >> 15;
    }

    private int getBlockPlusYRun(int index) {
      return (blocks[index] & 0x1f00000) >> 20;
    }

    private int getBlockPlusZRun(int index) {
      return (blocks[index] & 0x3e000000) >> 25;
    }

    private boolean isBlockFilled(int index) {
      return (blocks[index] & 0x40000000) != 0;
    }

    private static String hex(int value) {
      return String.format("%X", value);
    }

    public String getDebugInfo() {
      updateTypeList();
      var buffer = new StringBuilder();
      buffer.append("Type map:\n");
      for (int i = 0; i < types.length; ++i) {
        buffer.append(
            String.format(
                "  %d -> [%dx] %s\n",
                i, i == 0 ? xyzVolume - numBlocks : typeFrequencies.get(i), types[i]));
      }
      buffer.append('\n');
      for (int y = ySize - 1; y >= 0; --y) {
        buffer.append("y=");
        buffer.append(hex(y));
        buffer.append(":\n");
        buffer.append("    x:");
        for (int i = 0; i < xSize; ++i) {
          buffer.append("        " + hex(i));
        }
        buffer.append('\n');
        for (int z = 0; z < zSize; ++z) {
          buffer.append("  z=");
          buffer.append(hex(z));
          buffer.append(":");
          for (int x = 0; x < xSize; ++x) {
            buffer.append(" ");
            int index = coordToIndex(x, y, z);
            buffer.append(
                String.format(
                    "%2d:%X,%X,%X",
                    getBlockType(index),
                    getBlockPlusXRun(index),
                    getBlockPlusYRun(index),
                    getBlockPlusZRun(index)));
          }
          buffer.append('\n');
        }
        buffer.append('\n');
      }
      return buffer.toString();
    }
  }
}
