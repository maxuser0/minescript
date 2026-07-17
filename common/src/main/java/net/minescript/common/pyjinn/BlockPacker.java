// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.pyjinn;

import static net.minescript.common.pyjinn.PyjinnUtil.toNullableIntArray;
import static net.minescript.common.pyjinn.PyjinnUtil.toRequiredIntArray;

import org.pyjinn.interpreter.Script.KeywordArgs;
import org.pyjinn.interpreter.Script.PyjDict;

/**
 * BlockPacker is a mutable collection of blocks.
 *
 * <p>Blocks can be added to a blockpacker by calling `setblock(...)`, `fill(...)`, and/or
 * `add_blockpack(...)`. To serialize blocks or write them to a world, a blockpacker can be "packed"
 * by calling pack() to create a compact snapshot of the blocks contained in the blockpacker in the
 * form of a new BlockPack. A blockpacker continues to store the same blocks it had before being
 * packed, and more blocks can be added thereafter.
 *
 * <p>For a collection of blocks that is immutable and serializable, see `BlockPack`.
 */
public class BlockPacker {
  private static final KeywordArgs EMPTY_KEYWORD_ARGS = new KeywordArgs();

  private final net.minescript.common.BlockPacker impl;

  /** Creates a new, empty blockpacker. */
  public BlockPacker() {
    impl = new net.minescript.common.BlockPacker();
  }

  /**
   * Sets a block within this BlockPacker.
   *
   * <p>Args: pos: position of a block to set block_type: block descriptor to set
   *
   * <p>Raises: `BlockPackerException` if blockpacker operation fails
   */
  public void setblock(Object pyPos, String blockType) {
    int[] pos = toRequiredIntArray(pyPos);
    impl.setblock(pos[0], pos[1], pos[2], blockType);
  }

  /**
   * Fills blocks within this BlockPacker.
   *
   * <p>Args: pos1, pos2: coordinates of opposing corners of a rectangular volume to fill
   * block_type: block descriptor to fill
   *
   * <p>Raises: `BlockPackerException` if blockpacker operation fails
   */
  public void fill(Object pyPos1, Object pyPos2, String blockType) {
    int[] pos1 = toRequiredIntArray(pyPos1);
    int[] pos2 = toRequiredIntArray(pyPos2);
    impl.fill(pos1[0], pos1[1], pos1[2], pos2[0], pos2[1], pos2[2], blockType);
  }

  /**
   * Adds the blocks within a BlockPack into this BlockPacker.
   *
   * <p>Args: blockpack: BlockPack from which to copy blocks
   */
  public void add_blockpack(BlockPack blockpack) {
    add_blockpack(blockpack, EMPTY_KEYWORD_ARGS);
  }

  /**
   * Adds the blocks within a BlockPack into this BlockPacker.
   *
   * <p>Args: blockpack: BlockPack from which to copy blocks rotation: rotation matrix to apply to
   * block coordinates before adding to blockpacker offset: offset to apply to block coordiantes
   * (applied after rotation)
   */
  public void add_blockpack(BlockPack blockpack, KeywordArgs kwargs) {
    int[] rotation = toNullableIntArray(kwargs.get("rotation"));
    int[] offset = toNullableIntArray(kwargs.get("offset"));
    blockpack.impl.getBlocks(
        new net.minescript.common.BlockPack.TransformedBlockConsumer(rotation, offset, impl));
  }

  /**
   * Packs blocks within this BlockPacker into a new BlockPack.
   *
   * <p>Returns: a new BlockPack containing a snapshot of blocks from this BlockPacker
   */
  BlockPack pack() {
    return pack(EMPTY_KEYWORD_ARGS);
  }

  /**
   * Packs blocks within this BlockPacker into a new BlockPack.
   *
   * <p>Args: comments: key, value pairs to include in the new BlockPack
   *
   * <p>Returns: a new BlockPack containing a snapshot of blocks from this BlockPacker
   */
  BlockPack pack(KeywordArgs kwargs) {
    var commentsToAdd = (PyjDict) kwargs.get("comments");
    var comments = impl.comments();
    for (var kvPair : commentsToAdd.items()) {
      comments.put(kvPair.__getitem__(0).toString(), kvPair.__getitem__(1).toString());
    }
    return new BlockPack(impl.pack());
  }
}
