// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayDeque;
import java.util.Deque;
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

  // Allocator for IDs of block types used across all tiles in this BlockPacker, serving as keys
  // into typeMap.
  private final IdAllocator idAllocator = new IdAllocator();
  private Map<String, Integer> typeMap = new HashMap<>();
  private Map<Integer, String> symbolMap = new HashMap<>();

  private boolean debug = false;

  public BlockPacker(int xTileSize, int yTileSize, int zTileSize) {
    // TODO(maxuser): Validate that volume fits within 15 bits.
    this.xTileSize = xTileSize;
    this.yTileSize = yTileSize;
    this.zTileSize = zTileSize;

    int voidId = idAllocator.allocateId();
    if (voidId != 0) {
      throw new IllegalStateException(
          String.format("Expected type ID of structure_void to be 0 but got %d", voidId));
    }
    typeMap.put("structure_void", voidId);
    symbolMap.put(voidId, "structure_void");
  }

  public void enableDebug() {
    debug = true;
  }

  public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String blockType) {
    // TODO(maxuser): Consider the following optimizations:
    // 1. look up blockType in typeMap only once, not once per block in the fill (definitely)
    // 2. compute intersections between the fill volume and tile boundaries (maybe)
    for (int x = x1; x <= x2; x++) {
      for (int y = y1; y <= y2; y++) {
        for (int z = z1; z <= z2; z++) {
          setblock(x, y, z, blockType);
        }
      }
    }
  }

  public void setblock(int x, int y, int z, String blockType) {
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
    int blockTypeId = typeMap.computeIfAbsent(blockType, k -> idAllocator.allocateId());
    symbolMap.putIfAbsent(blockTypeId, blockType);
    tile.setBlock(x, y, z, blockTypeId);
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
    for (var entry : symbolMap.entrySet()) {
      System.out.printf("# symbol: %d -> %s\n", entry.getKey(), entry.getValue());
    }

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
      System.out.print(tile.getDebugInfo(symbolMap));
    }
  }

  public BlockPack pack() {
    var packedTiles = new TreeMap<Long, BlockPack.Tile>();
    int packBytes = 0;
    int runLengthBytes = 0;
    int bytes = 0;

    for (var entry : tiles.entrySet()) {
      long key = entry.getKey();
      var tile = entry.getValue();

      tile.computeRunLengths();
      tile.updateTypeList();
      ShortList fills = new ShortList();
      ShortList setblocks = new ShortList();
      tile.computeBlockCommands(fills, setblocks);
      var packedTile =
          new BlockPack.Tile(
              tile.xOffset,
              tile.yOffset,
              tile.zOffset,
              tile.types,
              fills.toArray(),
              setblocks.toArray());
      packedTiles.put(key, packedTile);
      if (debug) {
        int tilePackBytes = setblocks.size() * 2 + fills.size() * 2;
        packBytes += tilePackBytes;

        int prevBlock = -1;
        int runLengths = 0;
        for (int i = 0; i < tile.blocks.length; ++i) {
          int block = tile.blocks[i];
          if (block == prevBlock) {
            continue;
          }
          prevBlock = block;
          ++runLengths;
        }
        int tileRunLengthBytes = runLengths * 4;
        runLengthBytes += tileRunLengthBytes;

        bytes += Math.min(tilePackBytes, tileRunLengthBytes);

        System.out.printf(
            "tilebytes: offset %d,%d,%d  packed %d  runlen %d  diff %d\n",
            tile.xOffset,
            tile.yOffset,
            tile.zOffset,
            tilePackBytes,
            tileRunLengthBytes,
            tileRunLengthBytes - tilePackBytes);
      }
    }

    if (debug) {
      int symbolBytes = 0;
      for (var entry : symbolMap.entrySet()) {
        symbolBytes += entry.getValue().length();
      }
      System.out.printf("symbolbytes: %d for %d entries\n", symbolBytes, symbolMap.size());
      System.out.printf(
          "totalbytes: min=%d packed=%d runlen=%d diff=%d\n",
          bytes, packBytes, runLengthBytes, runLengthBytes - packBytes);
    }

    // TODO(maxuser): Remove entries from symbolMap that are no longer referenced. Entries become
    // unreferenced when the last block of a particular type is overwritten via
    // BlockPacker.setBlock(...) with a different type of block.

    // Mappings in symbolMap are snapshotted by the BlockPack constructor so that symbols referenced
    // in a BlockPack are stable in the face of the BlockPacker that created it adding or removing
    // entries.
    return new BlockPack(minX, minY, minZ, symbolMap, packedTiles);
  }

  static class IdAllocator {
    private Deque<Integer> freelist = new ArrayDeque<>();
    private int nextId = 0;

    public int allocateId() {
      if (freelist.isEmpty()) {
        return nextId++;
      }
      return freelist.removeFirst();
    }

    public void freeId(int id) {
      freelist.addLast(id);
    }
  }

  public static class ShortList {
    private int size = 0;
    private short[] shorts = new short[64];

    public ShortList() {}

    public ShortList addCoord(int x, int y, int z) {
      // TODO(maxuser): Throw an exception if x, y, and z aren't in range [0, 15].
      return add((short) ((x << 10) | (y << 5) | z));
    }

    public ShortList add(short i) {
      if (size >= shorts.length) {
        short[] newInts = new short[2 * shorts.length];
        System.arraycopy(shorts, 0, newInts, 0, shorts.length);
        shorts = newInts;
      }
      shorts[size++] = i;
      return this;
    }

    public int size() {
      return size;
    }

    public short get(int i) {
      return shorts[i];
    }

    public short[] toArray() {
      short[] copy = new short[size];
      System.arraycopy(shorts, 0, copy, 0, size);
      return copy;
    }
  }

  public static class Tile {
    private final int xOffset;
    private final int yOffset;
    private final int zOffset;
    private final int xSize;
    private final int ySize;
    private final int zSize;
    private final int xzArea;

    // Maps packer-level block type ID to tile-level block type ID.
    private Map<Integer, Short> tileTypeMap = new HashMap<>();

    // Maps tile-level block type ID to block type frequency within this tile.
    private Map<Short, Integer> typeFrequencies = new HashMap<>();
    private int[] types;

    // Allocator for IDs of block types used in this tile.
    private final IdAllocator tileIdAllocator = new IdAllocator();

    private int maxFrequencyType = -1; // written in computeRunLengths, read in computeBlockCommands
    private boolean prefillVolume = false; // TODO(maxuser): allow this to be enabled
    private static final int STRUCTURE_VOID_BLOCK = 0;

    // Block type value from tileTypeMap (15 bits = 32768 unique values)
    private final short[] blocks;

    // Each value in blocks stores data in these bits:
    // - bits 0-4: consecutive blocks of same type in +x direction
    // - bits 5-9: consecutive blocks of same type in +y direction
    // - bits 10-14: consecutive blocks of same type in +z direction
    // - bit 15: 1 if this block has been filled during packing, 0 otherwise
    private short[] blockMetrics;

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
      final int xyzVolume = xzArea * ySize;

      blocks = new short[xyzVolume];
      blockMetrics = null; // initialized in computeRunLengths() with same size as `blocks` array
      var voidId = tileTypeId(STRUCTURE_VOID_BLOCK);
      if (voidId != 0) {
        throw new IllegalStateException(
            String.format("Expected type ID of structure_void to be 0 but got %d", voidId));
      }
      typeFrequencies.put(voidId, xyzVolume);
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

    private short tileTypeId(int packerTypeId) {
      return tileTypeMap.computeIfAbsent(
          packerTypeId,
          k -> {
            int id = tileIdAllocator.allocateId();
            if (id < 0 || id > Short.MAX_VALUE) {
              throw new IllegalStateException(
                  String.format(
                      "BlockPacker.Tile allocated block type ID outside of expected range 0..%d:"
                          + " %d",
                      Short.MAX_VALUE, id));
            }
            return (short) id;
          });
    }

    private void updateTypeList() {
      if (types != null && types.length == tileTypeMap.size()) {
        return;
      }
      types = new int[tileTypeMap.size()];
      for (var entry : tileTypeMap.entrySet()) {
        types[entry.getValue()] = entry.getKey();
      }
    }

    public void setBlock(int x, int y, int z, int blockTypeId) {
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
      final short type = tileTypeId(blockTypeId);
      final short previousBlockType = setBlockType(x - xOffset, y - yOffset, z - zOffset, type);
      if (type != previousBlockType) {
        // Decrement frequency of previous block type and increment frequency of new block type.
        final var previousBlockTypeFrequency = typeFrequencies.get(previousBlockType) - 1;
        if (previousBlockTypeFrequency == 0) {
          typeFrequencies.remove(previousBlockType);
          tileIdAllocator.freeId(previousBlockType);
        } else {
          typeFrequencies.put(previousBlockType, previousBlockTypeFrequency);
        }
        typeFrequencies.put(type, typeFrequencies.computeIfAbsent(type, k -> 0) + 1);
      }
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

      blockMetrics = new short[blocks.length];

      int maxFrequency = 0;
      for (var entry : typeFrequencies.entrySet()) {
        var type = entry.getKey();
        var frequency = entry.getValue();
        if (frequency > maxFrequency) {
          maxFrequencyType = type;
          maxFrequency = frequency;
        }
      }

      // Compute the runs of consecutive blocks of the same type in each dimension: x, y, and z.
      for (int y = ySize - 1; y >= 0; --y) {
        for (int z = zSize - 1; z >= 0; --z) {
          for (int x = xSize - 1; x >= 0; --x) {
            int index = coordToIndex(x, y, z);
            short type = getBlockType(index);
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

    public void computeBlockCommands(ShortList fills, ShortList setblocks) {
      // Use the runs of consecutive blocks of the same type computed above to identify x,z-plane
      // areas and x,y,z rectangular volumes of the same type of block.
      for (int y = 0; y < ySize; ++y) {
        for (int x = 0; x < xSize; ++x) {
          for (int z = 0; z < zSize; ++z) {
            int index = coordToIndex(x, y, z);
            if (isBlockPacked(index)) {
              continue;
            }

            final short blockType = getBlockType(index);
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
                setblocks.addCoord(x, y, z).add(blockType);
              } else {
                fills.addCoord(x, y, z).addCoord(maxAreaX, y + minYRun, maxAreaZ).add(blockType);
              }
              for (int x2 = x; x2 < maxAreaX + 1; ++x2) {
                for (int z2 = z; z2 < maxAreaZ + 1; ++z2) {
                  for (int y2 = y; y2 < y + minYRun + 1; ++y2) {
                    setBlockPacked(x2, y2, z2);
                  }
                }
              }
            }
          }
        }
      }
    }

    private short setBlockType(int x, int y, int z, short type) {
      int index = coordToIndex(x, y, z);
      var previousBlockType = blocks[index];
      blocks[index] = type;
      return previousBlockType;
    }

    private void setBlockPacked(int x, int y, int z) {
      int index = coordToIndex(x, y, z);
      blockMetrics[index] |= (1 << 15);
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

      // TODO(maxuser): Is reading blockMetrics[index] for the packed bit necessary? Or is that
      // always zero at this point? If the latter, then don't need to read blockMetrics, only need
      // to overwrite it.
      int value = blockMetrics[index];
      value = (value & (1 << 15)) | plusXRun | (plusYRun << 5) | (plusZRun << 10);
      blockMetrics[index] = (short) value;
    }

    private short getBlockType(int index) {
      return blocks[index];
    }

    private int getBlockPlusXRun(int index) {
      return (blockMetrics[index] & 0x1f);
    }

    private int getBlockPlusYRun(int index) {
      return (blockMetrics[index] & (0x1f << 5)) >> 5;
    }

    private int getBlockPlusZRun(int index) {
      return (blockMetrics[index] & (0x1f << 10)) >> 10;
    }

    private boolean isBlockPacked(int index) {
      return (blockMetrics[index] & (1 << 15)) != 0;
    }

    private static String hex(int value) {
      return String.format("%X", value);
    }

    public String getDebugInfo(Map<Integer, String> symbolMap) {
      updateTypeList();
      var buffer = new StringBuilder();
      buffer.append("Type map:\n");
      for (int i = 0; i < types.length; ++i) {
        if (typeFrequencies.containsKey((short) i)) {
          buffer.append(
              String.format(
                  "  %d -> [%dx] %d -> %s\n",
                  i, typeFrequencies.get((short) i), types[i], symbolMap.get(types[i])));
        }
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
