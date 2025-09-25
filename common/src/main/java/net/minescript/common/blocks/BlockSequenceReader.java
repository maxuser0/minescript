package net.minescript.common.blocks;

import java.util.List;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;

/**
 * Utility class for converting a sequence of block positions into an array of block state strings.
 *
 * <p>This class is designed to handle various collection types that might represent a list of block
 * positions, such as Java {@link java.util.List}s, arrays, or Pyjinn-specific sequence types like
 * tuples and lists.
 *
 * <p>It optimizes the process by inspecting the first element of the sequence to determine the most
 * efficient way to read all subsequent positions. It creates a specialized {@link
 * BlockPositionReader} for the entire sequence, avoiding costly type checks inside the loop.
 *
 * @see BlockPositionReader
 */
public class BlockSequenceReader {
  private static final Logger LOGGER = LogManager.getLogger();

  /**
   * Converts a sequence of block positions into an array of block state strings.
   *
   * <p>This method accepts various types of sequences for {@code blockPositions}, including {@code
   * List<?>}, {@code Object[]}, and Pyjinn's {@code Script.Lengthable} (which covers Python lists
   * and tuples). It determines the appropriate reading strategy based on the type of the collection
   * and the type of its first element.
   *
   * <p>Each element in the {@code blockPositions} sequence is expected to be a representation of
   * [x, y, z] coordinates, such as a {@code List<Number>}, an {@code int[]}, or a Pyjinn tuple of
   * integers.
   *
   * @param level The world level to read block states from.
   * @param blockPositions A sequence of block position objects. Supported types are {@link List},
   *     {@link Script.Lengthable}, and {@code Object[]}.
   * @return An array of strings, where each string is the block state at the corresponding position
   *     in the input sequence.
   * @throws IllegalArgumentException if {@code blockPositions} is not a supported sequence type.
   */
  public static String[] toStringArray(Level level, Object blockPositions) {
    if (blockPositions instanceof List<?> positions) {
      LOGGER.info(
          "Identified getblocklist sequence type: List<?> ({})", positions.getClass().getName());
      return positionListToBlockStrings(level, positions);
    } else if (blockPositions instanceof Script.Lengthable positions) {
      LOGGER.info(
          "Identified getblocklist sequence type: Script.Lengthable ({})",
          positions.getClass().getName());
      return positionLengthableToBlockStrings(level, positions);
    } else if (blockPositions instanceof Object[] positions) {
      LOGGER.info("Identified getblocklist sequence type: Object[]");
      return positionArrayToBlockStrings(level, positions);
    } else {
      throw new IllegalArgumentException(
          "`getblocklist` expected a list, tuple, or array of (x, y, z) positions but got %s"
              .formatted(blockPositions == null ? "null" : blockPositions.getClass().getName()));
    }
  }

  private static String[] positionListToBlockStrings(Level level, List<?> positions) {
    String[] blocks = new String[positions.size()];
    int blockIndex = -1;
    BlockPositionReader blockReader = null; // Populate on first block read.
    for (var position : positions) {
      if (++blockIndex == 0) {
        blockReader = BlockPositionReader.create(level, position);
      }
      blocks[blockIndex] = blockReader.getblock(position);
    }
    return blocks;
  }

  private static String[] positionLengthableToBlockStrings(
      Level level, Script.Lengthable positions) {
    String[] blocks = new String[positions.__len__()];
    int blockIndex = -1;
    BlockPositionReader blockReader = null; // Populate on first block read.
    for (var position : positions) {
      if (++blockIndex == 0) {
        blockReader = BlockPositionReader.create(level, position);
      }
      blocks[blockIndex] = blockReader.getblock(position);
    }
    return blocks;
  }

  private static String[] positionArrayToBlockStrings(Level level, Object[] positions) {
    String[] blocks = new String[positions.length];
    int blockIndex = -1;
    BlockPositionReader blockReader = null; // Populate on first block read.
    for (var position : positions) {
      if (++blockIndex == 0) {
        blockReader = BlockPositionReader.create(level, position);
      }
      blocks[blockIndex] = blockReader.getblock(position);
    }
    return blocks;
  }
}
