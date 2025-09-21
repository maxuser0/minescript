// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.pyjinn;

import static net.minescript.common.pyjinn.PyjinnUtil.toNullableIntArray;
import static net.minescript.common.pyjinn.PyjinnUtil.toRequiredIntArray;

import java.util.Map;
import net.minescript.common.BlockPacker;
import net.minescript.common.Message;
import net.minescript.common.Minescript;
import org.pyjinn.interpreter.Script.KeywordArgs;
import org.pyjinn.interpreter.Script.PyjDict;
import org.pyjinn.interpreter.Script.PyjTuple;

/**
 * BlockPack is an immutable and serializable collection of blocks.
 *
 * <p>A blockpack can be read from or written to worlds, files, and serialized bytes. Although
 * blockpacks are immutable and preserve position and orientation of blocks, they can be rotated and
 * offset when read from or written to worlds.
 *
 * <p>For a mutable collection of blocks, see `BlockPacker`.
 */
public class BlockPack {
  private static final KeywordArgs EMPTY_KEYWORD_ARGS = new KeywordArgs();

  // Package-private so that it's accessible from BlockPacker.
  final net.minescript.common.BlockPack impl;

  /**
   * Constructs a new BlockPack by wrapping an internal BlockPack implementation.
   *
   * @param blockpack The internal BlockPack instance.
   */
  public BlockPack(net.minescript.common.BlockPack blockpack) {
    impl = blockpack;
  }

  /**
   * Reads a region of the world into a new BlockPack.
   *
   * @param pos1 The first corner of the region.
   * @param pos2 The second corner of the region.
   * @return A new BlockPack containing the blocks from the specified region.
   */
  public static BlockPack read_world(Object pos1, Object pos2) {
    return read_world(pos1, pos2, EMPTY_KEYWORD_ARGS);
  }

  /**
   * Reads a region of the world into a new BlockPack with optional transformations.
   *
   * @param pos1 The first corner of the region.
   * @param pos2 The second corner of the region.
   * @param kwargs Optional keyword arguments:
   *     <ul>
   *       <li>`rotation` (int[3]): Rotation to apply to the blocks.
   *       <li>`offset` (int[3]): Offset to apply to the block positions.
   *       <li>`comments` (dict): Comments to add to the blockpack.
   *       <li>`safety_limit` (bool): Whether to enforce a safety limit on the number of blocks
   *           read. Defaults to true.
   *     </ul>
   *
   * @return A new BlockPack containing the blocks from the specified region.
   */
  public static BlockPack read_world(Object pos1, Object pos2, KeywordArgs kwargs) {
    int[] rotation = toNullableIntArray(kwargs.get("rotation"));
    int[] offset = toNullableIntArray(kwargs.get("offset"));
    int[] p1 = toRequiredIntArray(pos1);
    int[] p2 = toRequiredIntArray(pos2);

    var blockpacker = new BlockPacker();

    var commentsToAdd = (PyjDict) kwargs.get("comments");
    if (commentsToAdd != null) {
      var comments = blockpacker.comments();
      for (var kvPair : commentsToAdd.items()) {
        comments.put(kvPair.__getitem__(0).toString(), kvPair.__getitem__(1).toString());
      }
    }

    boolean safetyLimit = (Boolean) kwargs.getOrDefault("safety_limit", true);

    Minescript.readBlocks(
        p1[0],
        p1[1],
        p1[2],
        p2[0],
        p2[1],
        p2[2],
        safetyLimit,
        new net.minescript.common.BlockPack.TransformedBlockConsumer(
            rotation, offset, blockpacker));
    return new BlockPack(blockpacker.pack());
  }

  /**
   * Reads a BlockPack from a .blockpack file.
   *
   * @param blockpackFilename The path to the .blockpack file.
   * @return A new BlockPack loaded from the file.
   * @throws Exception if an error occurs during file reading.
   */
  public static BlockPack read_file(String blockpackFilename) throws Exception {
    return new BlockPack(net.minescript.common.BlockPack.readZipFile(blockpackFilename));
  }

  /**
   * Deserializes a BlockPack from a Base64 encoded string.
   *
   * @param base64Data The Base64 encoded string representing a BlockPack.
   * @return A new BlockPack instance.
   */
  public static BlockPack import_data(String base64Data) {
    return new BlockPack(net.minescript.common.BlockPack.fromBase64EncodedString(base64Data));
  }

  /**
   * Returns the bounding box of the blocks in the pack.
   *
   * @return A tuple containing two tuples: the minimum and maximum coordinates `((x_min, y_min,
   *     z_min), (x_max, y_max, z_max))`.
   */
  //  Returns: Tuple[Tuple[int, int, int], Tuple[int, int, int]]
  public PyjTuple block_bounds() {
    int[] bounds = impl.blockBounds();

    var minBound = new PyjTuple(new Object[] {bounds[0], bounds[1], bounds[2]});
    var maxBound = new PyjTuple(new Object[] {bounds[3], bounds[4], bounds[5]});

    return new PyjTuple(new Object[] {minBound, maxBound});
  }

  /**
   * Returns the comments associated with the blockpack.
   *
   * @return A dictionary of comments.
   */
  public PyjDict comments() {
    return new PyjDict((Map) impl.comments());
  }

  /**
   * Writes the BlockPack to the world.
   *
   * @return True if the operation was successful.
   */
  public boolean write_world() {
    return write_world(EMPTY_KEYWORD_ARGS);
  }

  /**
   * Writes the BlockPack to the world with optional transformations.
   *
   * @param kwargs Optional keyword arguments:
   *     <ul>
   *       <li>`rotation` (int[3]): Rotation to apply to the blocks.
   *       <li>`offset` (int[3]): Offset to apply to the block positions.
   *     </ul>
   *
   * @return True if the operation was successful.
   */
  public boolean write_world(KeywordArgs kwargs) {
    int[] rotation = toNullableIntArray(kwargs.get("rotation"));
    int[] offset = toNullableIntArray(kwargs.get("offset"));
    impl.getBlockCommands(
        rotation,
        offset,
        s -> Minescript.systemMessageQueue.add(Message.createMinecraftCommand(s)));
    return true;
  }

  /**
   * Writes the BlockPack to a .blockpack file.
   *
   * @param blockpackFilename The path to the file to be written.
   * @return True if the file was written successfully.
   * @throws Exception if an error occurs during file writing.
   */
  public boolean write_file(String blockpackFilename) throws Exception {
    impl.writeZipFile(blockpackFilename);
    return true;
  }

  /**
   * Serializes the BlockPack to a Base64 encoded string.
   *
   * @return The Base64 encoded string representation of the BlockPack.
   */
  public String export_data() {
    return impl.toBase64EncodedString();
  }

  /** A functional interface for consuming individual block placements. */
  public interface SetblockConsumer {
    /**
     * Invoked by visit_blocks() for each block in the BlockPack that's not adjacent to any blocks
     * of the same type.
     *
     * @param x The block's x-coordinate.
     * @param y The block's y-coordinate.
     * @param z The block's z-coordinate.
     * @param block The block state string.
     */
    void setblock(int x, int y, int z, String block);
  }

  /** A functional interface for consuming fill operations. */
  public interface FillConsumer {
    /**
     * Invoked by visit_blocks() for each axis-aligned bounding box (aabb) of blocks of the same
     * type greater than 1x1x1 between opposing corners (x1, y1, z1) and (x2, y2, z2).
     *
     * @param x1 One corner's x-coordinate.
     * @param y1 One corner's y-coordinate.
     * @param z1 One corner's z-coordinate.
     * @param x2 The opposing corner's x-coordinate.
     * @param y2 The opposing corner's y-coordinate.
     * @param z2 The opposing corner's z-coordinate.
     * @param block The block state string.
     */
    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block);
  }

  /**
   * Iterates over the blocks in the blockpack, invoking the provided consumers for each setblock or
   * fill operation.
   *
   * @param blockConsumer A consumer for individual block placements.
   * @param fillConsumer A consumer for fill operations.
   */
  public void visit_blocks(SetblockConsumer blockConsumer, FillConsumer fillConsumer) {
    impl.getBlocks(
        new net.minescript.common.BlockPack.BlockConsumer() {
          @Override
          public void setblock(int x, int y, int z, String block) {
            blockConsumer.setblock(x, y, z, block);
          }

          @Override
          public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
            fillConsumer.fill(x1, y1, z1, x2, y2, z2, block);
          }
        });
  }
}
