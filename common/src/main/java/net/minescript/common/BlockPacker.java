// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * {@code BlockPacker} manages blocks and packs them into a {@code BlockPack}.
 *
 * <p>While {@code BlockPacker} manages a dynamic set of blocks, {@code BlockPack} is immutable.
 */
public class BlockPacker implements BlockPack.BlockConsumer {
  private final SortedMap<Long, Tile> tiles = new TreeMap<>();

  // Allocator for IDs of block types used across all tiles in this BlockPacker, serving as keys
  // into typeMap.
  private final IdAllocator idAllocator = new IdAllocator();
  private Map<String, Integer> typeMap = new HashMap<>();
  private Map<Integer, String> symbolMap = new HashMap<>();
  private Map<String, String> comments = new HashMap<>();

  private boolean debug = false;

  /**
   * Constructs a new BlockPacker.
   *
   * <p>Initializes the packer and reserves the block type ID 0 for "structure_void", which is used
   * to represent empty space within a tile.
   */
  public BlockPacker() {
    int voidId = idAllocator.allocateId();
    if (voidId != 0) {
      throw new IllegalStateException(
          String.format("Expected type ID of structure_void to be 0 but got %d", voidId));
    }
    typeMap.put("structure_void", voidId);
    symbolMap.put(voidId, "structure_void");
  }

  /** Enables the printing of debug information during the packing process. */
  public void enableDebug() {
    debug = true;
  }

  private static short[] convertBytesToShorts(byte[] bytes) {
    if (bytes.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Expected array with even number of bytes but got " + bytes.length);
    }
    short[] shorts = new short[bytes.length / 2];
    for (int i = 0; i < shorts.length; i++) {
      // Short bytes are in network byte order, i.e. big-endian.
      shorts[i] = (short) ((bytes[i * 2] & 0xff) << 8 | (bytes[i * 2 + 1] & 0xff));
    }
    return shorts;
  }

  /**
   * Adds a collection of blocks defined by Base64-encoded setblock and fill operations.
   *
   * @param offsetX The X-coordinate offset to apply to all block positions.
   * @param offsetY The Y-coordinate offset to apply to all block positions.
   * @param offsetZ The Z-coordinate offset to apply to all block positions.
   * @param setblocksBase64 A Base64 string representing an array of setblock operations.
   * @param fillsBase64 A Base64 string representing an array of fill operations.
   * @param blocks A list of block type strings, serving as a symbol table.
   */
  public void addBlocks(
      int offsetX,
      int offsetY,
      int offsetZ,
      String setblocksBase64,
      String fillsBase64,
      List<String> blocks) {
    short[] setblocksArray = convertBytesToShorts(Base64.getDecoder().decode(setblocksBase64));
    short[] fillsArray = convertBytesToShorts(Base64.getDecoder().decode(fillsBase64));
    addBlocks(offsetX, offsetY, offsetZ, setblocksArray, fillsArray, blocks);
  }

  /**
   * Adds a collection of blocks defined by setblock and fill operation arrays.
   *
   * @param offsetX The X-coordinate offset to apply to all block positions.
   * @param offsetY The Y-coordinate offset to apply to all block positions.
   * @param offsetZ The Z-coordinate offset to apply to all block positions.
   * @param setblocksArray An array of setblock operations.
   * @param fillsArray An array of fill operations.
   * @param blocks A list of block type strings, serving as a symbol table.
   */
  public void addBlocks(
      int offsetX,
      int offsetY,
      int offsetZ,
      short[] setblocksArray,
      short[] fillsArray,
      List<String> blocks) {
    if (fillsArray.length % 7 != 0) {
      throw new IllegalArgumentException(
          "Expected `fills` array with length divisible by 7 but got " + fillsArray.length);
    }
    if (setblocksArray.length % 4 != 0) {
      throw new IllegalArgumentException(
          "Expected `setblocks` array with length divisible by 4 but got " + setblocksArray.length);
    }
    for (int i = 0; i < fillsArray.length; i += 7) {
      fill(
          offsetX + fillsArray[i],
          offsetY + fillsArray[i + 1],
          offsetZ + fillsArray[i + 2],
          offsetX + fillsArray[i + 3],
          offsetY + fillsArray[i + 4],
          offsetZ + fillsArray[i + 5],
          blocks.get(fillsArray[i + 6]));
    }
    for (int i = 0; i < setblocksArray.length; i += 4) {
      setblock(
          offsetX + setblocksArray[i],
          offsetY + setblocksArray[i + 1],
          offsetZ + setblocksArray[i + 2],
          blocks.get(setblocksArray[i + 3]));
    }
  }

  @Override
  public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String blockType) {
    // TODO(maxuser): Consider the following optimizations:
    // 1. look up blockType in typeMap only once, not once per block in the fill (definitely)
    // 2. compute intersections between the fill volume and tile boundaries (maybe)
    int minX = Math.min(x1, x2);
    int maxX = Math.max(x1, x2);
    int minY = Math.min(y1, y2);
    int maxY = Math.max(y1, y2);
    int minZ = Math.min(z1, z2);
    int maxZ = Math.max(z1, z2);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          setblock(x, y, z, blockType);
        }
      }
    }
  }

  @Override
  public void setblock(int x, int y, int z, String blockType) {
    long key = BlockPack.getTileKey(x, y, z);
    Tile tile =
        tiles.computeIfAbsent(
            key,
            k -> {
              return new Tile(
                  BlockPack.getXFromPackedCoords(key),
                  BlockPack.getYFromPackedCoords(key),
                  BlockPack.getZFromPackedCoords(key));
            });
    int blockTypeId = typeMap.computeIfAbsent(blockType, k -> idAllocator.allocateId());
    symbolMap.putIfAbsent(blockTypeId, blockType);
    tile.setBlock(x, y, z, blockTypeId);
  }

  /** Prints detailed debug information about the internal state of the packer to standard out. */
  public void printDebugInfo() {
    for (var entry : symbolMap.entrySet()) {
      System.out.printf("# symbol: %d -> %s\n", entry.getKey(), entry.getValue());
    }

    for (Tile tile : tiles.values()) {
      int x = tile.xOffset;
      int y = tile.yOffset;
      int z = tile.zOffset;
      System.out.printf("# tile offset: %d %d %d\n", x, y, z);
      System.out.print(tile.getDebugInfo(symbolMap));
    }
  }

  /**
   * Returns the map of comments associated with this packer.
   *
   * @return A map of comments.
   */
  public Map<String, String> comments() {
    return comments;
  }

  /**
   * Processes the added blocks, optimizes them into run-lengths and fills, and creates an immutable
   * {@link BlockPack}.
   *
   * @return A new {@code BlockPack} instance containing the packed block data.
   */
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
    return new BlockPack(symbolMap, packedTiles, comments);
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

  /** A helper class for managing a dynamic list of shorts. */
  public static class ShortList {
    private int size = 0;
    private short[] shorts = new short[64];

    /** Constructs a new, empty ShortList. */
    public ShortList() {}

    /**
     * Packs tile-local coordinates into a short and adds it to the list.
     *
     * @param x The x-coordinate, in range [0, 31].
     * @param y The y-coordinate, in range [0, 31].
     * @param z The z-coordinate, in range [0, 31].
     * @return This ShortList instance for chaining.
     */
    public ShortList addCoord(int x, int y, int z) {
      // TODO(maxuser): Throw an exception if x, y, and z aren't in range [0, 15].
      return add((short) ((x << 10) | (y << 5) | z));
    }

    /**
     * Adds a short value to the end of the list.
     *
     * @param i The short to add.
     * @return This ShortList instance for chaining.
     */
    public ShortList add(short i) {
      if (size >= shorts.length) {
        short[] newInts = new short[2 * shorts.length];
        System.arraycopy(shorts, 0, newInts, 0, shorts.length);
        shorts = newInts;
      }
      shorts[size++] = i;
      return this;
    }

    /**
     * Returns the number of elements in the list.
     *
     * @return The size of the list.
     */
    public int size() {
      return size;
    }

    /**
     * Retrieves the short at the specified index.
     *
     * @param i The index of the element to return.
     * @return The short value at the specified index.
     */
    public short get(int i) {
      return shorts[i];
    }

    /**
     * Returns a new array containing all the elements in this list.
     *
     * @return A new short array with the contents of the list.
     */
    public short[] toArray() {
      short[] copy = new short[size];
      System.arraycopy(shorts, 0, copy, 0, size);
      return copy;
    }
  }

  /**
   * Represents a mutable 3D grid of blocks within a BlockPacker, corresponding to a BlockPack.Tile.
   */
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

    /**
     * Constructs a new Tile with default dimensions (32x32x32).
     *
     * @param xOffset The world x-coordinate of the tile's origin.
     * @param yOffset The world y-coordinate of the tile's origin.
     * @param zOffset The world z-coordinate of the tile's origin.
     */
    public Tile(int xOffset, int yOffset, int zOffset) {
      this(
          xOffset,
          yOffset,
          zOffset,
          BlockPack.X_TILE_SIZE,
          BlockPack.Y_TILE_SIZE,
          BlockPack.Z_TILE_SIZE);
    }

    /**
     * Constructs a new Tile with specified dimensions.
     *
     * @param xOffset The world x-coordinate of the tile's origin.
     * @param yOffset The world y-coordinate of the tile's origin.
     * @param zOffset The world z-coordinate of the tile's origin.
     * @param xSize The size of the tile along the X-axis.
     * @param ySize The size of the tile along the Y-axis.
     * @param zSize The size of the tile along the Z-axis.
     */
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

    /**
     * Converts tile-local 3D coordinates to a 1D array index.
     *
     * @param x The x-coordinate within the tile.
     * @param y The y-coordinate within the tile.
     * @param z The z-coordinate within the tile.
     * @return The corresponding 1D index in the `blocks` array.
     */
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

    /**
     * Sets the block type at the given world coordinates.
     *
     * @param x The world x-coordinate.
     * @param y The world y-coordinate.
     * @param z The world z-coordinate.
     * @param blockTypeId The packer-level ID of the block type.
     */
    public void setBlock(int x, int y, int z, int blockTypeId) {
      if (x < xOffset
          || x >= xOffset + xSize
          || y < yOffset
          || y >= yOffset + ySize
          || z < zOffset
          || z >= zOffset + zSize) {
        throw new ArrayIndexOutOfBoundsException(
            String.format(
                "Coord (%d, %d, %d) out of bounds for volume [%d..%d, %d..%d, %d..%d]",
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

    /**
     * Analyzes the block data to compute run-lengths of identical adjacent blocks in the X, Y, and
     * Z directions. This is a prerequisite for {@link #computeBlockCommands}.
     */
    public void computeRunLengths() {
      // Algorithm:
      // 1. Iterate x, y, z from 31 to 0 to accumulate run lengths in positive x, y, z direction.
      // 2. Iterate by y slice from 0 to 31
      //    - For each unfilled x, z block from (0, y, 0) to (31, y, 31):
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

    /**
     * Uses the pre-computed run-lengths to generate optimized fill and setblock commands for this
     * tile.
     *
     * @param fills A ShortList to be populated with fill command data.
     * @param setblocks A ShortList to be populated with setblock command data.
     */
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

    /**
     * Generates a string containing detailed debug information for this tile.
     *
     * @param symbolMap The packer-level symbol map to resolve block type names.
     * @return A formatted string for debugging.
     */
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
