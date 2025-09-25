package net.minescript.common.blocks;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;

/**
 * Interface for reading block states from various representations of block positions.
 *
 * <p>This abstraction provides a unified way to handle different data structures that might
 * represent a sequence of [x, y, z] coordinates, such as Java {@link java.util.List}s, arrays, or
 * Pyjinn-specific sequence types.
 *
 * <p>The primary use case is to create a specific reader instance once using the {@link
 * #create(Level, Object)} factory method based on a sample position object, with the assumption
 * that all positions in the sequence will have uniform number and type of values. This specialized
 * reader can then be used in a loop to read blocks from a large collection of positions without
 * incurring the overhead of type-checking each position object on every iteration.
 *
 * @see #create(Level, Object)
 */
public sealed interface BlockPositionReader
    permits BlockPositionReader.JavaList,
        BlockPositionReader.JavaObjectArray,
        BlockPositionReader.JavaIntegerArray,
        BlockPositionReader.JavaIntArray,
        BlockPositionReader.PyjinnItemGetter {
  static final Logger LOGGER = LogManager.getLogger();

  /**
   * Retrieves the string representation of the block at the given position.
   *
   * @param position An object representing the [x, y, z] coordinates of the block. The concrete
   *     type of this object depends on the specific implementation of this reader.
   * @return The string representation of the block state (e.g., "minecraft:stone"), or null if the
   *     block state cannot be determined.
   */
  String getblock(Object position);

  // BlockState#toString() returns a string formatted as:
  // "Block{minecraft:acacia_button}[face=floor,facing=west,powered=false]"
  //
  // BLOCK_STATE_RE helps transform this to:
  // "minecraft:acacia_button[face=floor,facing=west,powered=false]"
  static Pattern BLOCK_STATE_RE = Pattern.compile("^Block\\{([^}]*)\\}(\\[.*\\])?$");

  /**
   * Converts a {@link BlockState} object to its string representation for use in Minescript.
   *
   * <p>This method parses the default string output of a {@link BlockState} object (e.g.,
   * "Block{minecraft:acacia_button}[face=floor,facing=west,powered=false]") and reformats it into a
   * concise string that Minescript can use (e.g.,
   * "minecraft:acacia_button[face=floor,facing=west,powered=false]").
   *
   * @param blockState The {@code BlockState} to convert.
   * @return The formatted string representation of the block state, or {@code null} if parsing
   *     fails.
   */
  public static String blockStateToString(BlockState blockState) {
    var match = BLOCK_STATE_RE.matcher(blockState.toString());
    if (!match.find()) {
      return null;
    }
    String blockType = match.group(1);
    String blockAttrs = match.group(2) == null ? "" : match.group(2);
    return blockType + blockAttrs;
  }

  /**
   * Retrieves the block state at a given position and converts it to its string representation.
   *
   * <p>This is a convenience method that combines getting the {@link BlockState} from the world and
   * then formatting it using {@link #blockStateToString(BlockState)}.
   *
   * @param level The world level to read the block state from.
   * @param pos The position of the block.
   * @return The formatted string representation of the block state, or {@code null} if parsing
   *     fails.
   * @see #blockStateToString(BlockState)
   */
  public static String getBlockStateString(Level level, BlockPos pos) {
    return blockStateToString(level.getBlockState(pos));
  }

  /**
   * Factory method to create a {@link BlockPositionReader} based on the type of position data.
   *
   * <p>This method inspects the type of a sample position object to determine the most efficient
   * way to read coordinate data from it. It supports various Java collection types as well as
   * Pyjinn-specific types. This allows for optimized block reading by avoiding repeated type checks
   * within loops.
   *
   * @param level The world level to read block states from.
   * @param samplePosition A sample object representing a block position, used for type detection.
   *     Supported types include {@code List<?>}, {@code Object[]}, {@code Integer[]}, {@code
   *     int[]}, and {@code Script.ItemGetter}. The elements are expected to be [x, y, z]
   *     coordinates.
   * @return A {@link BlockPositionReader} instance tailored to the type of {@code samplePosition}.
   * @throws IllegalArgumentException if {@code samplePosition} is of an unsupported type or null.
   */
  public static BlockPositionReader create(Level level, Object samplePosition) {
    if (samplePosition instanceof List<?>) {
      LOGGER.info(
          "Identified getblocklist position type: List<?> ({})",
          samplePosition.getClass().getName());
      return new JavaList(level, new BlockPos.MutableBlockPos());
    } else if (samplePosition instanceof Object[]) {
      LOGGER.info("Identified getblocklist position type: Object[]");
      return new JavaObjectArray(level, new BlockPos.MutableBlockPos());
    } else if (samplePosition instanceof Integer[]) {
      LOGGER.info("Identified getblocklist position type: Integer[]");
      return new JavaIntegerArray(level, new BlockPos.MutableBlockPos());
    } else if (samplePosition instanceof int[]) {
      LOGGER.info("Identified getblocklist position type: int[]");
      return new JavaIntArray(level, new BlockPos.MutableBlockPos());
    } else if (samplePosition instanceof Script.ItemGetter) {
      LOGGER.info(
          "Identified getblocklist position type: Script.ItemGetter ({})",
          samplePosition.getClass().getName());
      return new PyjinnItemGetter(level, new BlockPos.MutableBlockPos());
    } else if (samplePosition == null) {
      throw new IllegalArgumentException(
          "getblocklist() expected a list, tuple, List<>, Object[], Integer[], or int[] of "
              + "[x, y, z] but got null");
    } else {
      throw new IllegalArgumentException(
          "getblocklist() expected a list, tuple, List<>, Object[], Integer[], or int[] of "
              + "[x, y, z] but got %s (%s)"
                  .formatted(samplePosition, samplePosition.getClass().getName()));
    }
  }

  /** Implementation of BlockPositionReader for {@code List<?>}. */
  record JavaList(Level level, BlockPos.MutableBlockPos reusablePos)
      implements BlockPositionReader {
    @Override
    public String getblock(Object position) {
      if (position instanceof List<?> list) {
        if (list.size() != 3
            || !(list.get(0) instanceof Number)
            || !(list.get(1) instanceof Number)
            || !(list.get(2) instanceof Number)) {
          throw new IllegalArgumentException(
              "getblocklist() expected List of [x, y, z] but got %s".formatted(list));
        }
        int x = ((Number) list.get(0)).intValue();
        int y = ((Number) list.get(1)).intValue();
        int z = ((Number) list.get(2)).intValue();
        return getBlockStateString(level, reusablePos.set(x, y, z));
      } else {
        throw new IllegalArgumentException(
            "getblocklist() expected List of [x, y, z] but got %s (%s)"
                .formatted(position, position.getClass().getName()));
      }
    }
  }

  /** Implementation of BlockPositionReader for Object[]. */
  record JavaObjectArray(Level level, BlockPos.MutableBlockPos reusablePos)
      implements BlockPositionReader {
    @Override
    public String getblock(Object position) {
      if (position instanceof Object[] array) {
        if (array.length != 3
            || !(array[0] instanceof Number)
            || !(array[1] instanceof Number)
            || !(array[2] instanceof Number)) {
          throw new IllegalArgumentException(
              "getblocklist() expected Object[] of [x, y, z] but got %s"
                  .formatted(Arrays.toString(array)));
        }
        int x = ((Number) array[0]).intValue();
        int y = ((Number) array[1]).intValue();
        int z = ((Number) array[2]).intValue();
        return getBlockStateString(level, reusablePos.set(x, y, z));
      } else {
        throw new IllegalArgumentException(
            "getblocklist() expected Object[] of [x, y, z] but got %s (%s)"
                .formatted(position, position.getClass().getName()));
      }
    }
  }

  /** Implementation of BlockPositionReader for Integer[]. */
  record JavaIntegerArray(Level level, BlockPos.MutableBlockPos reusablePos)
      implements BlockPositionReader {
    @Override
    public String getblock(Object position) {
      if (position instanceof Integer[] array) {
        if (array.length != 3) {
          throw new IllegalArgumentException(
              "getblocklist() expected Integer[] of [x, y, z] but got %s"
                  .formatted(Arrays.toString(array)));
        }
        int x = array[0];
        int y = array[1];
        int z = array[2];
        return getBlockStateString(level, reusablePos.set(x, y, z));
      } else {
        throw new IllegalArgumentException(
            "getblocklist() expected Integer[] of [x, y, z] but got %s (%s)"
                .formatted(position, position.getClass().getName()));
      }
    }
  }

  /** Implementation of BlockPositionReader for int[]. */
  record JavaIntArray(Level level, BlockPos.MutableBlockPos reusablePos)
      implements BlockPositionReader {
    @Override
    public String getblock(Object position) {
      if (position instanceof int[] array) {
        if (array.length != 3) {
          throw new IllegalArgumentException(
              "getblocklist() expected int[] of [x, y, z] but got %s"
                  .formatted(Arrays.toString(array)));
        }
        int x = array[0];
        int y = array[1];
        int z = array[2];
        return getBlockStateString(level, reusablePos.set(x, y, z));
      } else {
        throw new IllegalArgumentException(
            "getblocklist() expected int[] of [x, y, z] but got %s (%s)"
                .formatted(position, position.getClass().getName()));
      }
    }
  }

  /** Implementation of BlockPositionReader for Pyjinn list and tuple (and dict, too). */
  record PyjinnItemGetter(Level level, BlockPos.MutableBlockPos reusablePos)
      implements BlockPositionReader {
    @Override
    public String getblock(Object position) {
      if (position instanceof Script.ItemGetter getter) {
        if (getter.__len__() != 3
            || !(getter.__getitem__(0) instanceof Number)
            || !(getter.__getitem__(1) instanceof Number)
            || !(getter.__getitem__(2) instanceof Number)) {
          throw new IllegalArgumentException(
              "getblocklist() expected a Pyjinn list or tuple of [x, y, z] but got %s"
                  .formatted(getter));
        }
        int x = ((Number) getter.__getitem__(0)).intValue();
        int y = ((Number) getter.__getitem__(1)).intValue();
        int z = ((Number) getter.__getitem__(2)).intValue();
        return getBlockStateString(level, reusablePos.set(x, y, z));
      } else {
        throw new IllegalArgumentException(
            "getblocklist() expected a Pyjinn list or tuple of [x, y, z] but got %s (%s)"
                .formatted(position, position.getClass().getName()));
      }
    }
  }
}
