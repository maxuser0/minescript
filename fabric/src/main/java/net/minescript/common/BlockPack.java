// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.TreeMap;

/**
 * {@code BlockPack} represents blocks stored as rectangular volumes of equivalent blocks.
 * <p>
 * {@code BlockPack} stores blocks for efficient translation into /fill and /setblock commands.
 */
public class BlockPack {
  private final int minX;
  private final int minY;
  private final int minZ;

  // TODO(maxuser): Currently using a TreeMap to preserve ordering, but the current ordering is
  // determined by the code that instantiates a BlockPack. For instance, the keys defined by
  // BlockPacker.getTileKey() intentionally encode the tile's y offset as the high bits of the key.
  // The y-first tile order means that blocks can be rendered naturally in bottom-up order so
  // flowing blocks like water in higher tiles follow stable blocks beneath them in lower tiles. But
  // other code that instantiates a BlockPack might order keys differently, so we should enforce
  // y-first key order within BlockPack itself.
  private final TreeMap<Long, Tile> tiles;

  public BlockPack(int minX, int minY, int minZ, TreeMap<Long, Tile> tiles) {
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.tiles = tiles;
  }

  // TODO(maxuser): Replace printBlockCommands with general-purpose iterability of tiles in layers
  // (see #Layering below).
  public void printBlockCommands(boolean offsetToOrigin, boolean setblockOnly) {
    for (Tile tile : tiles.values()) {
      tile.printBlockCommands(offsetToOrigin, setblockOnly, minX, minY, minZ);
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

    // TODO(maxuser): Consider using ImmutableList<String> for immutability.
    private final String[] blockTypes;

    // Every 4 ints represents: x, y, z, block_type
    // TODO(maxuser): Don't need 4 32-bit ints since each value requires only 5 bits.
    // TODO(maxuser): Consider subclassing IntList with SetblockList.
    public IntList setblocks = new IntList();

    // Every 7 ints represents: x1, y1, z1, x2, y2, z2, block_type
    // TODO(maxuser): Don't need 7 32-bit ints since each value requires only 5 bits.
    // TODO(maxuser): Consider subclassing IntList with FillList.
    public IntList fills = new IntList();

    public Tile(String[] blockTypes, int xOffset, int yOffset, int zOffset) {
      this.blockTypes = blockTypes;
      this.xOffset = xOffset;
      this.yOffset = yOffset;
      this.zOffset = zOffset;
    }

    public static class IntList {
      private int size = 0;
      private int[] ints = new int[64];

      public IntList() {}

      public IntList add(int i) {
        if (size >= ints.length) {
          int[] newInts = new int[2 * ints.length];
          System.arraycopy(ints, 0, newInts, 0, ints.length);
          ints = newInts;
        }
        ints[size++] = i;
        return this;
      }

      public int size() {
        return size;
      }

      public int get(int i) {
        return ints[i];
      }
    }

    public void printBlockCommands(
        boolean offsetToOrigin, boolean setblockOnly, int minX, int minY, int minZ) {
      for (int i = 0; i < fills.size(); i += 7) {
        int x1 = xOffset + fills.get(i);
        int y1 = yOffset + fills.get(i + 1);
        int z1 = zOffset + fills.get(i + 2);
        int x2 = xOffset + fills.get(i + 3);
        int y2 = yOffset + fills.get(i + 4);
        int z2 = zOffset + fills.get(i + 5);
        if (offsetToOrigin) {
          x1 -= minX;
          y1 -= minY;
          z1 -= minZ;
          x2 -= minX;
          y2 -= minY;
          z2 -= minZ;
        }
        String blockType = blockTypes[fills.get(i + 6)];
        if (setblockOnly) {
          for (int x = x1; x <= x2; ++x) {
            for (int y = y1; y <= y2; ++y) {
              for (int z = z1; z <= z2; ++z) {
                System.out.printf("/setblock %d %d %d %s\n", x, y, z, blockType);
              }
            }
          }
        } else {
          System.out.printf("/fill %d %d %d %d %d %d %s\n", x1, y1, z1, x2, y2, z2, blockType);
        }
      }
      for (int i = 0; i < setblocks.size(); i += 4) {
        int x = xOffset + setblocks.get(i);
        int y = yOffset + setblocks.get(i + 1);
        int z = zOffset + setblocks.get(i + 2);
        if (offsetToOrigin) {
          x -= minX;
          y -= minY;
          z -= minZ;
        }
        String blockType = blockTypes[setblocks.get(i + 3)];
        System.out.printf("/setblock %d %d %d %s\n", x, y, z, blockType);
      }
    }
  }
}
