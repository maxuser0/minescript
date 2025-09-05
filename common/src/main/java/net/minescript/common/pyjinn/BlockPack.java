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
import org.pyjinn.interpreter.Script.PyDict;
import org.pyjinn.interpreter.Script.PyTuple;

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

  public BlockPack(net.minescript.common.BlockPack blockpack) {
    impl = blockpack;
  }

  public static BlockPack read_world(Object pos1, Object pos2) {
    return read_world(pos1, pos2, EMPTY_KEYWORD_ARGS);
  }

  public static BlockPack read_world(Object pos1, Object pos2, KeywordArgs kwargs) {
    int[] rotation = toNullableIntArray(kwargs.get("rotation"));
    int[] offset = toNullableIntArray(kwargs.get("offset"));
    int[] p1 = toRequiredIntArray(pos1);
    int[] p2 = toRequiredIntArray(pos2);

    var blockpacker = new BlockPacker();

    var commentsToAdd = (PyDict) kwargs.get("comments");
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

  public static BlockPack read_file(String blockpackFilename) throws Exception {
    return new BlockPack(net.minescript.common.BlockPack.readZipFile(blockpackFilename));
  }

  public static BlockPack import_data(String base64Data) {
    return new BlockPack(net.minescript.common.BlockPack.fromBase64EncodedString(base64Data));
  }

  //  Returns: Tuple[Tuple[int, int, int], Tuple[int, int, int]]
  public PyTuple block_bounds() {
    int[] bounds = impl.blockBounds();

    var minBound = new PyTuple(new Object[] {bounds[0], bounds[1], bounds[2]});
    var maxBound = new PyTuple(new Object[] {bounds[3], bounds[4], bounds[5]});

    return new PyTuple(new Object[] {minBound, maxBound});
  }

  public PyDict comments() {
    return new PyDict((Map) impl.comments());
  }

  public boolean write_world() {
    return write_world(EMPTY_KEYWORD_ARGS);
  }

  public boolean write_world(KeywordArgs kwargs) {
    int[] rotation = toNullableIntArray(kwargs.get("rotation"));
    int[] offset = toNullableIntArray(kwargs.get("offset"));
    impl.getBlockCommands(
        rotation,
        offset,
        s -> Minescript.systemMessageQueue.add(Message.createMinecraftCommand(s)));
    return true;
  }

  public boolean write_file(String blockpackFilename) throws Exception {
    impl.writeZipFile(blockpackFilename);
    return true;
  }

  public String export_data() {
    return impl.toBase64EncodedString();
  }
}
