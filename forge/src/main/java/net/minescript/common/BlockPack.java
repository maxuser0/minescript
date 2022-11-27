// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code BlockPack} represents blocks stored as rectangular volumes of equivalent blocks.
 *
 * <p>{@code BlockPack} stores blocks for efficient translation into /fill and /setblock commands.
 */
public class BlockPack {
  private final int minX;
  private final int minY;
  private final int minZ;

  // Symbol table for mapping BlockPack-level block ID to symbolic block-type string.
  private final Map<Integer, BlockType> symbolMap = new HashMap<>();

  // TODO(maxuser): Currently using a TreeMap to preserve ordering, but the current ordering is
  // determined by the code that instantiates a BlockPack. For instance, the keys defined by
  // BlockPacker.getTileKey() intentionally encode the tile's y offset as the high bits of the key.
  // The y-first tile order means that blocks can be rendered naturally in bottom-up order so
  // flowing blocks like water in higher tiles follow stable blocks beneath them in lower tiles. But
  // other code that instantiates a BlockPack might order keys differently, so we should enforce
  // y-first key order within BlockPack itself.
  private final TreeMap<Long, Tile> tiles;

  private static class BlockType {
    public final String symbol;
    public final boolean stable;

    public BlockType(String symbol) {
      this.symbol = symbol;

      // Blocks are considered stable by default, and unstable if falling or flowing.
      //
      // Falling blocks:
      // - anvil
      // - *_concrete_powder (several colors)
      // - dragon_egg
      // - gravel
      // - pointed_dripstone
      // - red_sand
      // - sand
      // - scaffolding
      //
      // Flowing blocks:
      // - lava
      // - water
      //
      // TODO(maxuser): Support unstable blocks.
      this.stable = true;
    }
  }

  public BlockPack(
      int minX, int minY, int minZ, Map<Integer, String> symbolMap, TreeMap<Long, Tile> tiles) {
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.tiles = tiles;

    for (var entry : symbolMap.entrySet()) {
      this.symbolMap.put(entry.getKey(), new BlockType(entry.getValue()));
    }
  }

  // TODO(maxuser): Replace printBlockCommands with general-purpose iterability of tiles in layers
  // (see #Layering below).
  public void printBlockCommands(boolean offsetToOrigin, boolean setblockOnly) {
    for (Tile tile : tiles.values()) {
      // TODO(maxuser): Use tile.printBlockCommands(...) for the initial "stable blocks" layer, and
      // tile.printBlockCommandsInAscendingYOrder(...) for the subsequent "unstable blocks" layer.
      tile.printBlockCommandsInAscendingYOrder(
          offsetToOrigin, setblockOnly, minX, minY, minZ, symbolMap);
    }
  }

  // TODO(maxuser): Update BlockPack.Tile to contain these fields:
  //
  // 1. ImmutableList<String> blockTypes: max size 2^15 == 32768 (max blocks in 32*32*32 volume);
  // or int[] blockTypeIndices: indices into a BlockPack-wide list/array of String to eliminate
  // String duplication. (It's ok for blockTypes to remain as String[] if it remains an
  // implementation detail and callers don't need to iterate or lookup values.)
  //
  // 2. int firstUnstableBlockType: index into blockTypes corresponding to the first index of
  // unstable blocks; all block types at indices below firstUnstableBlockType are treated as stable
  // blocks.  See #Layering below for details.
  //
  // 3. int[] setblocks: (or 2*16-bit for consistency with fills array)
  // - bits 0-14: block type value within 32*32*32 BlockPack.Tile
  // - bit 15: reserved
  // - bits 16-20: x position within BlockPack.Tile
  // - bits 21-25: y position within BlockPack.Tile
  // - bits 26-30: z position within BlockPack.Tile
  // - bit 31: reserved
  //
  // 4. short[] fills: 3*16-bit fill-style volumes:
  // - bits 0-14: block type value within 32*32*32 BlockPack.Tile
  // - bit 15: reserved
  // - bits 16-20: min x position within BlockPack.Tile
  // - bits 21-25: min y position within BlockPack.Tile
  // - bits 26-30: min z position within BlockPack.Tile
  // - bit 31: reserved
  // - bits 32-36: max x position within BlockPack.Tile
  // - bits 37-41: max y position within BlockPack.Tile
  // - bits 42-46: max z position within BlockPack.Tile
  // - bit 47: reserved
  //
  // BlockPack is not designed for repeated random access by x, y, z position. Code (in Java or
  // Python) that wishes to visit blocks repeatedly at specific positions should expand a BlockPack
  // into a format that's optimized for random access by inflating BlockPack.Tile into a
  // position-indexed array by iterating over Tile.setblocks and Tile.fills.
  //
  // #Layering
  // When a BlockPack is placed as blocks into a world, volumes in tiles must be placed in two
  // sequential layers: all volumes corresponding to the first layer must be placed before any of
  // the volumes corresponding to the second layer.  The first layer places "stable blocks" that do
  // not fall, do not flow, and do not require adjacent blocks for placements; the second layer
  // places "unstable blocks" which can fall, flow, or require adjacent blocks for placement. (A
  // third, middle layer may be used for unknown blocks like those defined in mods.) Volumes are
  // stored within a BlockPack's tiles irrespective of layer; layers are imposed by iterating
  // volumes across tiles twice, each time with a mutually exclusive filter applied. Values within
  // Tile.blockTypes[] are ordered so that stable block types have lower indices than unstable block
  // types; all indices less than Tile.firstUnstableBlockType correspond to stable blocks; all
  // indices at least Tile.firstUnstableBlockType correspond to unstable blocks.
  public static class Tile {
    private final int xOffset;
    private final int yOffset;
    private final int zOffset;

    private final int[] blockTypes;

    // Every 3 shorts represents: (x1, y1, z1), (x2, y2, z2), block_type
    private final short[] fills;

    // Every 2 shorts represents: (x, y, z) and block_type
    private final short[] setblocks;

    // blockTypes contains indices into BlockPack symbol table.
    public Tile(
        int xOffset, int yOffset, int zOffset, int[] blockTypes, short[] fills, short[] setblocks) {
      this.blockTypes = blockTypes;
      this.xOffset = xOffset;
      this.yOffset = yOffset;
      this.zOffset = zOffset;
      this.setblocks = setblocks;
      this.fills = fills;
    }

    public void printBlockCommands(
        boolean offsetToOrigin,
        boolean setblockOnly,
        int minX,
        int minY,
        int minZ,
        Map<Integer, BlockType> symbolMap) {
      int[] coord = new int[3];

      for (int i = 0; i < fills.length; i += 3) {
        getCoord(fills, i, coord);
        int x1 = xOffset + coord[0];
        int y1 = yOffset + coord[1];
        int z1 = zOffset + coord[2];

        getCoord(fills, i + 1, coord);
        int x2 = xOffset + coord[0];
        int y2 = yOffset + coord[1];
        int z2 = zOffset + coord[2];

        if (offsetToOrigin) {
          x1 -= minX;
          y1 -= minY;
          z1 -= minZ;
          x2 -= minX;
          y2 -= minY;
          z2 -= minZ;
        }
        int blockTypeId = blockTypes[fills[i + 2]];
        BlockType blockType = symbolMap.get(blockTypeId);
        if (setblockOnly) {
          for (int x = x1; x <= x2; ++x) {
            for (int y = y1; y <= y2; ++y) {
              for (int z = z1; z <= z2; ++z) {
                System.out.printf("/setblock %d %d %d %s\n", x, y, z, blockType.symbol);
              }
            }
          }
        } else {
          System.out.printf(
              "/fill %d %d %d %d %d %d %s\n", x1, y1, z1, x2, y2, z2, blockType.symbol);
        }
      }
      for (int i = 0; i < setblocks.length; i += 2) {
        getCoord(setblocks, i, coord);
        int x = xOffset + coord[0];
        int y = yOffset + coord[1];
        int z = zOffset + coord[2];
        if (offsetToOrigin) {
          x -= minX;
          y -= minY;
          z -= minZ;
        }
        int blockTypeId = blockTypes[setblocks[i + 1]];
        BlockType blockType = symbolMap.get(blockTypeId);
        System.out.printf("/setblock %d %d %d %s\n", x, y, z, blockType.symbol);
      }
    }

    private static int[] getCoord(short[] shorts, int i, int[] coord) {
      short s = shorts[i];
      coord[0] = s >> 10;
      coord[1] = (s >> 5) & ((1 << 5) - 1);
      coord[2] = s & ((1 << 5) - 1);
      return coord;
    }

    public void printBlockCommandsInAscendingYOrder(
        boolean offsetToOrigin,
        boolean setblockOnly,
        int minX,
        int minY,
        int minZ,
        Map<Integer, BlockType> symbolMap) {
      final int setblocksSize = setblocks.length;
      final int fillsSize = fills.length;

      int setblocksPos = 0;
      int fillsPos = 0;
      int[] coord = new int[3];
      while (setblocksPos < setblocksSize || fillsPos < fillsSize) {
        int fillsY = fillsPos < fillsSize ? getCoord(fills, fillsPos, coord)[1] : Integer.MAX_VALUE;
        int setblocksY =
            setblocksPos < setblocksSize
                ? getCoord(setblocks, setblocksPos, coord)[1]
                : Integer.MAX_VALUE;

        if (fillsY <= setblocksY) {
          // fill at fillsPos.
          int i = fillsPos;
          getCoord(fills, i, coord);
          int x1 = xOffset + coord[0];
          int y1 = yOffset + coord[1];
          int z1 = zOffset + coord[2];
          getCoord(fills, i + 1, coord);
          int x2 = xOffset + coord[0];
          int y2 = yOffset + coord[1];
          int z2 = zOffset + coord[2];
          if (offsetToOrigin) {
            x1 -= minX;
            y1 -= minY;
            z1 -= minZ;
            x2 -= minX;
            y2 -= minY;
            z2 -= minZ;
          }
          int blockTypeId = blockTypes[fills[i + 2]];
          BlockType blockType = symbolMap.get(blockTypeId);
          if (setblockOnly) {
            for (int x = x1; x <= x2; ++x) {
              for (int y = y1; y <= y2; ++y) {
                for (int z = z1; z <= z2; ++z) {
                  System.out.printf("/setblock %d %d %d %s\n", x, y, z, blockType.symbol);
                }
              }
            }
          } else {
            System.out.printf(
                "/fill %d %d %d %d %d %d %s\n", x1, y1, z1, x2, y2, z2, blockType.symbol);
          }
          fillsPos += 3;
        }

        if (setblocksY < fillsY) {
          // setblock at setblocksPos
          int i = setblocksPos;
          getCoord(setblocks, i, coord);
          int x = xOffset + coord[0];
          int y = yOffset + coord[1];
          int z = zOffset + coord[2];
          if (offsetToOrigin) {
            x -= minX;
            y -= minY;
            z -= minZ;
          }
          int blockTypeId = blockTypes[setblocks[i + 1]];
          BlockType blockType = symbolMap.get(blockTypeId);
          System.out.printf("/setblock %d %d %d %s\n", x, y, z, blockType.symbol);
          setblocksPos += 2;
        }
      }
    }
  }
}
