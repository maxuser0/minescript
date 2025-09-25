// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * {@code BlockPack} represents blocks stored as rectangular volumes of equivalent blocks.
 *
 * <p>{@code BlockPack} stores blocks for efficient translation into fill and setblock commands.
 */
public class BlockPack {
  private static final int X_BUILD_MIN = -(1 << 25);
  private static final int X_BUILD_MAX = (1 << 25) - 1;
  private static final int Y_BUILD_MIN = -64;
  private static final int Y_BUILD_MAX = 319;
  private static final int Z_BUILD_MIN = -(1 << 25);
  private static final int Z_BUILD_MAX = (1 << 25) - 1;

  // X_TILE_SIZE * Y_TILE_SIZE * Z_TILE_SIZE must fit within 16 bits.
  /** The size of a tile along the X-axis. */
  public static final int X_TILE_SIZE = 32;

  /** The size of a tile along the Y-axis. */
  public static final int Y_TILE_SIZE = 32;

  /** The size of a tile along the Z-axis. */
  public static final int Z_TILE_SIZE = 32;

  private static final long MASK_26_BITS = (1L << 26) - 1;
  private static final long MASK_12_BITS = (1L << 12) - 1;

  private static final String FILE_FORMAT_MAGIC_BYTES = "BLOCPAK!";

  // Size threshold for cutting a new "Tile" chunk.
  private static final int TILE_CHUNK_THRESHOLD_BYTES = 16384;

  // Symbol table for mapping BlockPack-level block ID to symbolic block-type string.
  private final Map<Integer, BlockType> symbolMap = new HashMap<>();

  // Using a SortedMap to preserve ordering with y representing the high bits of the key; see
  // #getTileKey(). The y-first tile order means that blocks can be rendered naturally in bottom-up
  // order so flowing blocks like water in higher tiles follow stable blocks beneath them in lower
  // tiles.
  private final SortedMap<Long, Tile> tiles;

  private final ImmutableMap<String, String> comments;

  /** The minimum X-coordinate of any tile in this blockpack. */
  public final int minTileX;

  /** The minimum Y-coordinate of any tile in this blockpack. */
  public final int minTileY;

  /** The minimum Z-coordinate of any tile in this blockpack. */
  public final int minTileZ;

  /** The maximum X-coordinate of any tile in this blockpack. */
  public final int maxTileX;

  /** The maximum Y-coordinate of any tile in this blockpack. */
  public final int maxTileY;

  /** The maximum Z-coordinate of any tile in this blockpack. */
  public final int maxTileZ;

  /** The minimum X-coordinate of any block in this blockpack. */
  public final int minBlockX;

  /** The minimum Y-coordinate of any block in this blockpack. */
  public final int minBlockY;

  /** The minimum Z-coordinate of any block in this blockpack. */
  public final int minBlockZ;

  /** The maximum X-coordinate of any block in this blockpack. */
  public final int maxBlockX;

  /** The maximum Y-coordinate of any block in this blockpack. */
  public final int maxBlockY;

  /** The maximum Z-coordinate of any block in this blockpack. */
  public final int maxBlockZ;

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

  private static int worldXToTileX(int x) {
    return ((x >= 0) ? (x / X_TILE_SIZE) : (((x + 1) / X_TILE_SIZE) - 1)) * X_TILE_SIZE;
  }

  private static int worldYToTileY(int y) {
    return ((y >= 0) ? (y / Y_TILE_SIZE) : (((y + 1) / Y_TILE_SIZE) - 1)) * Y_TILE_SIZE;
  }

  private static int worldZToTileZ(int z) {
    return ((z >= 0) ? (z / Z_TILE_SIZE) : (((z + 1) / Z_TILE_SIZE) - 1)) * Z_TILE_SIZE;
  }

  /**
   * Calculates a key for a tile based on world coordinates.
   *
   * @param x The world x-coordinate.
   * @param y The world y-coordinate.
   * @param z The world z-coordinate.
   * @return A long key representing the tile containing the given coordinates.
   */
  public static long getTileKey(int x, int y, int z) {
    return packCoords(worldXToTileX(x), worldYToTileY(y), worldZToTileZ(z));
  }

  /**
   * Packs tile coordinates into a single 64-bit long value.
   *
   * @param x The tile x-coordinate.
   * @param y The tile y-coordinate.
   * @param z The tile z-coordinate.
   * @return The packed long value.
   */
  public static long packCoords(int x, int y, int z) {
    // Adjust x, y, z to non-negative values so they're amenable to bit operations.
    int xOffset = x - X_BUILD_MIN;
    int yOffset = y - Y_BUILD_MIN;
    int zOffset = z - Z_BUILD_MIN;

    // y gets the high 12 bits, x gets the next 26 bits, and z gets the low 26 bits.
    return (((long) yOffset & MASK_12_BITS) << 26 + 26)
        | (((long) xOffset & MASK_26_BITS) << 26)
        | ((long) zOffset & MASK_26_BITS);
  }

  /**
   * Extracts the X-coordinate from a packed coordinate key.
   *
   * @param key The packed long key.
   * @return The tile X-coordinate.
   */
  public static int getXFromPackedCoords(long key) {
    return (int) ((key >>> 26) & MASK_26_BITS) + X_BUILD_MIN;
  }

  /**
   * Extracts the Y-coordinate from a packed coordinate key.
   *
   * @param key The packed long key.
   * @return The tile Y-coordinate.
   */
  public static int getYFromPackedCoords(long key) {
    return (int) (key >>> (26 + 26)) + Y_BUILD_MIN;
  }

  /**
   * Extracts the Z-coordinate from a packed coordinate key.
   *
   * @param key The packed long key.
   * @return The tile Z-coordinate.
   */
  public static int getZFromPackedCoords(long key) {
    return (int) (key & MASK_26_BITS) + Z_BUILD_MIN;
  }

  /**
   * Constructs a BlockPack from its underlying data structures.
   *
   * @param symbolMap A map from internal block IDs to their string representations.
   * @param tiles A sorted map of tile keys to Tile objects.
   * @param comments A map of comments associated with the blockpack.
   */
  public BlockPack(
      Map<Integer, String> symbolMap, SortedMap<Long, Tile> tiles, Map<String, String> comments) {
    this.tiles = tiles;
    this.comments = ImmutableMap.copyOf(comments);

    for (var entry : symbolMap.entrySet()) {
      this.symbolMap.put(entry.getKey(), new BlockType(entry.getValue()));
    }

    if (tiles.isEmpty()) {
      minTileX = minTileY = minTileZ = 0;
      maxTileX = maxTileY = maxTileZ = 0;
      minBlockX = minBlockY = minBlockZ = 0;
      maxBlockX = maxBlockY = maxBlockZ = 0;
      return;
    }

    // First iterate all tiles to find the min/max tile along each dimension.
    int minTileX = X_BUILD_MAX;
    int minTileY = Y_BUILD_MAX;
    int minTileZ = Z_BUILD_MAX;
    int maxTileX = X_BUILD_MIN;
    int maxTileY = Y_BUILD_MIN;
    int maxTileZ = Z_BUILD_MIN;
    for (long key : tiles.keySet()) {
      int x = getXFromPackedCoords(key);
      int y = getYFromPackedCoords(key);
      int z = getZFromPackedCoords(key);

      if (x < minTileX) minTileX = x;
      if (y < minTileY) minTileY = y;
      if (z < minTileZ) minTileZ = z;

      if (x > maxTileX) maxTileX = x;
      if (y > maxTileY) maxTileY = y;
      if (z > maxTileZ) maxTileZ = z;
    }
    this.minTileX = minTileX;
    this.minTileY = minTileY;
    this.minTileZ = minTileZ;
    this.maxTileX = maxTileX + X_TILE_SIZE - 1; // offset to max end of tile
    this.maxTileY = maxTileY + Y_TILE_SIZE - 1; // offset to max end of tile
    this.maxTileZ = maxTileZ + Z_TILE_SIZE - 1; // offset to max end of tile

    // Next find the tiles matching the min/max to find the min/max block in those border tiles.
    int minBlockX = X_BUILD_MAX;
    int minBlockY = Y_BUILD_MAX;
    int minBlockZ = Z_BUILD_MAX;
    int maxBlockX = X_BUILD_MIN;
    int maxBlockY = Y_BUILD_MIN;
    int maxBlockZ = Z_BUILD_MIN;
    int[] coord = new int[3];
    for (var entry : tiles.entrySet()) {
      long key = entry.getKey();
      int tileX = getXFromPackedCoords(key);
      int tileY = getYFromPackedCoords(key);
      int tileZ = getZFromPackedCoords(key);

      Tile tile = entry.getValue();
      if (tileX == minTileX
          || tileX == maxTileX
          || tileY == minTileY
          || tileY == maxTileY
          || tileZ == minTileZ
          || tileZ == maxTileZ) {
        for (int i = 0; i < tile.fills.length; ++i) {
          if (i % 3 == 2) continue;
          Tile.getCoord(tile.fills[i], coord);

          int blockX = tileX + coord[0];
          if (blockX < minBlockX) minBlockX = blockX;
          if (blockX > maxBlockX) maxBlockX = blockX;

          int blockY = tileY + coord[1];
          if (blockY < minBlockY) minBlockY = blockY;
          if (blockY > maxBlockY) maxBlockY = blockY;

          int blockZ = tileZ + coord[2];
          if (blockZ < minBlockZ) minBlockZ = blockZ;
          if (blockZ > maxBlockZ) maxBlockZ = blockZ;
        }
        for (int i = 0; i < tile.setblocks.length; i += 2) {
          Tile.getCoord(tile.setblocks[i], coord);

          int blockX = tileX + coord[0];
          if (blockX < minBlockX) minBlockX = blockX;
          if (blockX > maxBlockX) maxBlockX = blockX;

          int blockY = tileY + coord[1];
          if (blockY < minBlockY) minBlockY = blockY;
          if (blockY > maxBlockY) maxBlockY = blockY;

          int blockZ = tileZ + coord[2];
          if (blockZ < minBlockZ) minBlockZ = blockZ;
          if (blockZ > maxBlockZ) maxBlockZ = blockZ;
        }
      }
    }
    this.minBlockX = minBlockX;
    this.minBlockY = minBlockY;
    this.minBlockZ = minBlockZ;
    this.maxBlockX = maxBlockX;
    this.maxBlockY = maxBlockY;
    this.maxBlockZ = maxBlockZ;
  }

  /**
   * Returns the comments associated with this blockpack.
   *
   * @return An immutable map of comments.
   */
  public ImmutableMap<String, String> comments() {
    return comments;
  }

  /**
   * Returns the bounding box of all blocks in this blockpack.
   *
   * @return An array of 6 integers: {minBlockX, minBlockY, minBlockZ, maxBlockX, maxBlockY,
   *     maxBlockZ}.
   */
  public int[] blockBounds() {
    return new int[] {minBlockX, minBlockY, minBlockZ, maxBlockX, maxBlockY, maxBlockZ};
  }

  private static void writeShortArray(DataOutputStream dataOut, short[] shorts) throws IOException {
    byte[] bytes = new byte[shorts.length * 2];
    dataOut.writeInt(shorts.length);
    for (int i = 0; i < shorts.length; i++) {
      bytes[2 * i] = (byte) (shorts[i] >>> 8);
      bytes[2 * i + 1] = (byte) (shorts[i] & 0xff);
    }
    dataOut.write(bytes);
  }

  private static short[] readShortArray(DataInputStream dataIn) throws IOException {
    int numShorts = dataIn.readInt();
    byte[] bytes = new byte[numShorts * 2];
    short[] shorts = new short[numShorts];
    dataIn.readFully(bytes);
    for (int i = 0; i < numShorts; i++) {
      shorts[i] = (short) (((bytes[2 * i]) << 8) | (bytes[2 * i + 1] & 0xff));
    }
    return shorts;
  }

  private static void writeIntArray(DataOutputStream dataOut, int[] ints) throws IOException {
    byte[] bytes = new byte[ints.length * 4];
    dataOut.writeInt(ints.length);
    for (int i = 0; i < ints.length; i++) {
      bytes[4 * i] = (byte) (ints[i] >>> 24);
      bytes[4 * i + 1] = (byte) ((ints[i] >>> 16) & 0xff);
      bytes[4 * i + 2] = (byte) ((ints[i] >>> 8) & 0xff);
      bytes[4 * i + 3] = (byte) (ints[i] & 0xff);
    }
    dataOut.write(bytes);
  }

  private static int[] readIntArray(DataInputStream dataIn) throws IOException {
    int numInts = dataIn.readInt();
    byte[] bytes = new byte[numInts * 4];
    int[] ints = new int[numInts];
    dataIn.readFully(bytes);
    for (int i = 0; i < numInts; i++) {
      ints[i] =
          (bytes[4 * i] << 24)
              | (bytes[4 * i + 1] << 16)
              | (bytes[4 * i + 2] << 8)
              | (bytes[4 * i + 3] & 0xff);
    }
    return ints;
  }

  private static void writeAsciiString(DataOutputStream dataOut, String string) throws IOException {
    dataOut.write(string.getBytes(StandardCharsets.US_ASCII));
  }

  private static String readAsciiString(DataInputStream dataIn, int length) throws IOException {
    byte[] bytes = new byte[length];
    dataIn.readFully(bytes);
    String asciiString = new String(bytes, StandardCharsets.US_ASCII);
    return asciiString;
  }

  private static void writeUtf8String(DataOutputStream dataOut, String string) throws IOException {
    byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
    dataOut.writeInt(bytes.length);
    dataOut.write(bytes);
  }

  private static String readUtf8String(DataInputStream dataIn) throws IOException {
    byte[] bytes = new byte[dataIn.readInt()];
    dataIn.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static int computeCrc32(byte[] bytes) {
    var crc32 = new CRC32();
    crc32.update(bytes);
    return (int) (crc32.getValue() & 0xffffffff);
  }

  /**
   * Serializes the BlockPack into a byte array.
   *
   * @return A byte array representing the BlockPack.
   * @throws RuntimeException if an I/O error occurs during serialization.
   */
  public byte[] toBytes() {
    try (var bytesOut = new ByteArrayOutputStream();
        var dataOut = new DataOutputStream(bytesOut)) {
      writeStream(dataOut);
      return bytesOut.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Serializes the BlockPack into a Base64 encoded string.
   *
   * @return A Base64 string representing the BlockPack.
   */
  public String toBase64EncodedString() {
    return Base64.getEncoder().encodeToString(toBytes());
  }

  /**
   * Writes the BlockPack to a zip file. The file will contain a single entry with a `.blox`
   * extension.
   *
   * @param filename The path to the output zip file.
   * @throws Exception if an I/O error occurs.
   */
  public void writeZipFile(String filename) throws Exception {
    // Add ".zip" if filename doesn't already end with it.
    int dotZipIndex = filename.toLowerCase().lastIndexOf(".zip");

    // Note that even if there's no dir separator found, lastDirSeparatorPos will be -1, and
    // creating a substring starting at `lastDirSeparatorPos + 1` will be correct (i.e. zero)
    // without explicitly checking for whether it was found.
    int lastDirSeparatorPos = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    final String filenameBase;

    if (dotZipIndex > 0) {
      filenameBase = filename.substring(lastDirSeparatorPos + 1, dotZipIndex);
    } else {
      filenameBase = filename.substring(lastDirSeparatorPos + 1);
      filename += ".zip";
    }

    try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(new File(filename)));
        DataOutputStream dataOut = new DataOutputStream(zipOut)) {
      zipOut.putNextEntry(new ZipEntry(filenameBase + ".blox"));
      zipOut.setLevel(6);
      writeStream(dataOut);
      zipOut.closeEntry();
    }
  }

  private interface ChunkWriter {
    void writeChunk(DataOutputStream dataOut) throws IOException;
  }

  private static void writeChunk(
      DataOutputStream dataOut, String chunkName, ChunkWriter chunkWriter) throws IOException {
    if (chunkName.length() != 4) {
      throw new IllegalArgumentException(
          String.format("Expected chunk name to have 4 chars but got \"%s\"", chunkName));
    }

    try (var chunkBytesOut = new ByteArrayOutputStream();
        var chunkDataOut = new DataOutputStream(chunkBytesOut)) {
      writeAsciiString(chunkDataOut, chunkName);
      chunkWriter.writeChunk(chunkDataOut);
      dataOut.writeInt(chunkDataOut.size() - 4); // exclude 4-char type string
      chunkBytesOut.writeTo(dataOut);
      dataOut.writeInt(computeCrc32(chunkBytesOut.toByteArray()));
    }
  }

  private static class ChunkReader {
    private final String chunkName;
    private final DataInputStream chunkDataIn;
    private final int chunkLength;

    public ChunkReader(DataInputStream dataIn) throws IOException {
      this(dataIn, null);
    }

    public ChunkReader(DataInputStream dataIn, String expectedChunkName) throws IOException {
      this.chunkLength = dataIn.readInt();
      byte[] chunkBytes = new byte[chunkLength + 4]; // include 4-char type string
      dataIn.readFully(chunkBytes);
      var chunkBytesIn = new ByteArrayInputStream(chunkBytes);
      this.chunkDataIn = new DataInputStream(chunkBytesIn);
      this.chunkName = readAsciiString(chunkDataIn, 4);
      if (expectedChunkName != null && !chunkName.equals(expectedChunkName)) {
        throw new IllegalArgumentException(
            String.format(
                "Expected chunk named \"%s\" but got \"%s\"", expectedChunkName, chunkName));
      }
      int recordedCrc = dataIn.readInt();
      int computedCrc = computeCrc32(chunkBytes);
      if (recordedCrc != computedCrc) {
        throw new IllegalArgumentException(
            String.format(
                "Computed chunk CRC32 0x%x does not match recorded CRC32 0x%x",
                computedCrc, recordedCrc));
      }
    }

    public String chunkName() {
      return chunkName;
    }

    DataInputStream dataInputStream() {
      return chunkDataIn;
    }

    int chunkLength() {
      return chunkLength;
    }
  }

  private void writeHeadChunk(DataOutputStream dataOut) throws IOException {
    int majorVersion = 1;
    int minorVersion = 0;
    int patchVersion = 0;
    dataOut.writeInt((majorVersion << 24) | (minorVersion << 16) | (patchVersion << 8));
  }

  private void writePaletteChunk(DataOutputStream dataOut) throws IOException {
    int maxSymbolId = symbolMap.isEmpty() ? -1 : Collections.max(symbolMap.keySet());
    dataOut.writeInt(maxSymbolId + 1);
    for (int i = 0; i < maxSymbolId + 1; ++i) {
      var blockType = symbolMap.get(i);
      writeUtf8String(dataOut, blockType.symbol == null ? "" : blockType.symbol);
    }
  }

  private static void readHeadChunk(DataInputStream dataIn) throws IOException {
    int version = dataIn.readInt();
    if (version >>> 24 != 1) {
      throw new IllegalArgumentException(
          String.format(
              "Expected version in blockpack zip entry to be v1.*.* but got v%d.%d.%d",
              version >>> 24, (version >>> 16) & 0xff, (version >>> 8) & 0xff));
    }
  }

  private static Map<Integer, String> readPaletteChunk(DataInputStream dataIn) throws IOException {
    Map<Integer, String> symbolMap = new HashMap<>();
    int symbolMapSize = dataIn.readInt();
    for (int i = 0; i < symbolMapSize; ++i) {
      symbolMap.put(i, readUtf8String(dataIn));
    }
    return symbolMap;
  }

  private static void readTileChunk(DataInputStream dataIn, SortedMap<Long, Tile> tiles)
      throws IOException {
    long numTiles = dataIn.readInt();

    for (int i = 0; i < numTiles; ++i) {
      long key = dataIn.readLong();
      int xOffset = getXFromPackedCoords(key);
      int yOffset = getYFromPackedCoords(key);
      int zOffset = getZFromPackedCoords(key);

      int[] blockTypes = readIntArray(dataIn);
      short[] fills = readShortArray(dataIn);
      short[] setblocks = readShortArray(dataIn);

      var tile = new Tile(xOffset, yOffset, zOffset, blockTypes, fills, setblocks);
      tiles.put(key, tile);
    }
  }

  private static void readTextChunk(
      DataInputStream dataIn, int textLength, Map<String, String> comments) throws IOException {
    byte[] bytes = new byte[textLength];
    dataIn.readFully(bytes);
    String text = new String(bytes, StandardCharsets.UTF_8);
    int nullPos = text.indexOf(0);
    if (nullPos == -1) {
      throw new IllegalArgumentException(
          String.format(
              "Comment in \"text\" chunk of blockpack is missing a null delimiter: \"%s\"", text));
    }
    String key = text.substring(0, nullPos);
    String value = text.substring(nullPos + 1);
    comments.put(key, value);
  }

  /** Writes "Tile" chunks, cutting new chunks whenever TILE_CHUNK_THRESHOLD_BYTES is exceeded. */
  private static class TileChunkWriter implements AutoCloseable {
    private final DataOutputStream dataOut;
    private final ByteArrayOutputStream tmpBytesOut;
    private final DataOutputStream tmpDataOut;
    private int numTilesPending = 0; // Number of tiles written to tmpBytesOut, but not to dataOut.

    public TileChunkWriter(DataOutputStream dataOut) {
      this.dataOut = dataOut;
      this.tmpBytesOut = new ByteArrayOutputStream();
      this.tmpDataOut = new DataOutputStream(this.tmpBytesOut);
    }

    public void write(long tileKey, Tile tile) throws IOException {
      tmpDataOut.writeLong(tileKey);
      writeIntArray(tmpDataOut, tile.blockTypes);
      writeShortArray(tmpDataOut, tile.fills);
      writeShortArray(tmpDataOut, tile.setblocks);

      ++numTilesPending;

      if (tmpBytesOut.size() > TILE_CHUNK_THRESHOLD_BYTES) {
        flushChunk();
      }
    }

    @Override
    public void close() throws Exception {
      if (tmpBytesOut.size() > 0) {
        flushChunk();
      }
    }

    private void flushChunk() throws IOException {
      writeChunk(
          dataOut,
          "Tile",
          d -> {
            d.writeInt(numTilesPending);
            tmpBytesOut.writeTo(d);
            tmpBytesOut.reset();
            numTilesPending = 0;
          });
    }
  }

  private void writeStream(DataOutputStream dataOut) throws Exception {
    writeAsciiString(dataOut, FILE_FORMAT_MAGIC_BYTES);
    writeChunk(dataOut, "Head", this::writeHeadChunk);

    for (var entry : comments.entrySet()) {
      writeChunk(
          dataOut,
          "text",
          d -> {
            d.write(
                String.format("%s\0%s", entry.getKey(), entry.getValue())
                    .getBytes(StandardCharsets.UTF_8));
          });
    }

    writeChunk(dataOut, "Plte", this::writePaletteChunk);

    try (var tileChunkWriter = new TileChunkWriter(dataOut)) {
      for (var entry : tiles.entrySet()) {
        tileChunkWriter.write(entry.getKey(), entry.getValue());
      }
    }

    writeChunk(dataOut, "Done", d -> {});
  }

  /**
   * Deserializes a BlockPack from a byte array.
   *
   * @param bytes The byte array containing BlockPack data.
   * @return A new BlockPack instance.
   * @throws RuntimeException if an I/O error occurs during deserialization.
   */
  public static BlockPack fromBytes(byte[] bytes) {
    try (var bytesIn = new ByteArrayInputStream(bytes);
        var dataIn = new DataInputStream(bytesIn)) {
      return readStream(dataIn);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Deserializes a BlockPack from a Base64 encoded string.
   *
   * @param base64 The Base64 string representing a BlockPack.
   * @return A new BlockPack instance.
   */
  public static BlockPack fromBase64EncodedString(String base64) {
    return fromBytes(Base64.getDecoder().decode(base64));
  }

  /**
   * Reads a BlockPack from a zip file.
   *
   * @param filename The path to the zip file.
   * @return A new BlockPack instance.
   * @throws Exception if an I/O error or format error occurs.
   */
  public static BlockPack readZipFile(String filename) throws Exception {
    // Add ".zip" if filename doesn't already end with it.
    int dotZipIndex = filename.toLowerCase().lastIndexOf(".zip");
    if (dotZipIndex <= 0) {
      filename += ".zip";
    }

    try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(new File(filename)));
        DataInputStream dataIn = new DataInputStream(zipIn)) {
      ZipEntry zipEntry;
      while ((zipEntry = zipIn.getNextEntry()) != null) {
        // Ignore the zip entries that don't end in ".blox".
        if (zipEntry.getName().toLowerCase().endsWith(".blox")) {
          return readStream(dataIn);
        }
      }
    }
    throw new IllegalArgumentException(
        "Expected an entry ending with `.blox` in the zip archive, but no such entry found in "
            + filename);
  }

  private static BlockPack readStream(DataInputStream dataIn) throws IOException {
    String op = null; // Name of current operation to faciliate better error reporting.
    try {
      op = "reading first 8 (magic) bytes";
      String first8Bytes = readAsciiString(dataIn, 8);
      if (!first8Bytes.equals(FILE_FORMAT_MAGIC_BYTES)) {
        throw new IllegalArgumentException(
            String.format(
                "Expected first 8 bytes of blockpack data to be \"%s\" but got \"%s\"",
                FILE_FORMAT_MAGIC_BYTES, first8Bytes));
      }

      // Track the chunks that are allowed only once.
      Set<String> exhaustedChunks = new HashSet<>();

      op = "reading blockpack `Head` chunk";
      ChunkReader chunkReader = new ChunkReader(dataIn, "Head");
      readHeadChunk(chunkReader.dataInputStream());
      exhaustedChunks.add(chunkReader.chunkName());

      Map<Integer, String> symbolMap = null;
      SortedMap<Long, Tile> tiles = new TreeMap<>();
      Map<String, String> comments = new HashMap<>();

      op = "reading blockpack chunk";
      chunkReader = new ChunkReader(dataIn);
      while (!chunkReader.chunkName().equals("Done")) {
        if (exhaustedChunks.contains(chunkReader.chunkName())) {
          throw new IllegalArgumentException(
              String.format(
                  "\"%s\" chunk appears more than once in blockpack", chunkReader.chunkName()));
        }
        switch (chunkReader.chunkName()) {
          case "Plte":
            exhaustedChunks.add(chunkReader.chunkName());
            symbolMap = readPaletteChunk(chunkReader.dataInputStream());
            break;

          case "Tile":
            readTileChunk(chunkReader.dataInputStream(), tiles);
            break;

          case "text":
            readTextChunk(chunkReader.dataInputStream(), chunkReader.chunkLength(), comments);
            break;

          default:
            if (Character.isUpperCase(chunkReader.chunkName().charAt(0))) {
              throw new IllegalArgumentException(
                  String.format(
                      "Unrecognized critical chunk named \"%s\" in blockpack",
                      chunkReader.chunkName()));
            }
        }
        chunkReader = new ChunkReader(dataIn);
      }

      if (symbolMap == null) {
        throw new IllegalArgumentException("Required chunk \"Plte\" not found in blockpack");
      }

      return new BlockPack(symbolMap, tiles, comments);
    } catch (IOException e) {
      if (e.getMessage() == null) {
        throw new IOException(e.getClass().getSimpleName() + " while " + op);
      } else {
        throw new IOException(e.getMessage() + " (while " + op + ")");
      }
    }
  }

  /**
   * A consumer for block placement operations, supporting both individual blocks and filled
   * regions.
   */
  public interface BlockConsumer {
    /**
     * Consumes a single block placement.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The z-coordinate.
     * @param block The block state string.
     */
    void setblock(int x, int y, int z, String block);

    /**
     * Consumes a rectangular region filled with a single block type.
     *
     * @param x1 The first x-coordinate.
     * @param y1 The first y-coordinate.
     * @param z1 The first z-coordinate.
     * @param x2 The second x-coordinate.
     * @param y2 The second y-coordinate.
     * @param z2 The second z-coordinate.
     * @param block The block state string.
     */
    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block);
  }

  /**
   * Map a direction ("north", "south", etc) to an (x, z) vector relative to (0, 0).
   *
   * <p>If {@code direction} is not "north", "south", "east", or "west", return false. Otherwise
   * populate {@code vector} with x and z in positions 0 and 1, respectively, and return true.
   */
  private static boolean mapDirectionToXZ(String direction, int[] vector) {
    switch (direction) {
      case "north":
        vector[0] = 0;
        vector[1] = -1;
        return true;
      case "south":
        vector[0] = 0;
        vector[1] = 1;
        return true;
      case "east":
        vector[0] = 1;
        vector[1] = 0;
        return true;
      case "west":
        vector[0] = -1;
        vector[1] = 0;
        return true;
    }
    return false;
  }

  /**
   * Map an (x, z) direction relative to (0, 0) to a direction: "north", "south", "east", "west".
   *
   * <p>If (x, z) is not a unit vector aligned with the x or z axes, return {@code null}.
   */
  private static String mapXZToDirection(int x, int z) {
    if (x == 0) {
      switch (z) {
        case -1:
          return "north";
        case 1:
          return "south";
      }
    }
    if (z == 0) {
      switch (x) {
        case -1:
          return "west";
        case 1:
          return "east";
      }
    }
    return null;
  }

  private static final Pattern BLOCK_FACING_RE = Pattern.compile("facing=([a-z]*)");

  private static String reorientBlock(String block, int[] rotation) {
    var match = BLOCK_FACING_RE.matcher(block);
    if (match.find()) {
      String direction = match.group(1);
      int[] vector = new int[] {0, 0};
      if (mapDirectionToXZ(direction, vector)) {
        int newDirectionX = rotation[0] * vector[0] + rotation[2] * vector[1];
        int newDirectionZ = rotation[6] * vector[0] + rotation[8] * vector[1];
        String newDirection = mapXZToDirection(newDirectionX, newDirectionZ);
        if (newDirection != null) {
          return match.replaceFirst("facing=" + newDirection);
        }
      }
    }
    return block;
  }

  /**
   * A {@link BlockConsumer} that transforms coordinates and block states before passing them to
   * another consumer.
   */
  public static class TransformedBlockConsumer implements BlockConsumer {
    private final int[] rotation;
    private final int[] offset;
    private final BlockConsumer blockConsumer;

    /**
     * Creates a BlockConsumer that transforms coordinates first through a rotation then a
     * translation.
     *
     * @param rotation A 9-element array representing a 3x3 rotation matrix. Can be null.
     * @param offset A 3-element array representing an (x, y, z) translation. Can be null.
     * @param blockConsumer The downstream consumer to receive the transformed block operations.
     */
    public TransformedBlockConsumer(int[] rotation, int[] offset, BlockConsumer blockConsumer) {
      if (rotation != null && rotation.length != 9) {
        throw new IllegalArgumentException(
            "Expected rotation to have 9 ints but got " + rotation.length);
      }
      if (offset != null && offset.length != 3) {
        throw new IllegalArgumentException(
            "Expected offset to have 3 ints but got " + offset.length);
      }

      this.rotation = rotation;
      this.offset = offset;
      this.blockConsumer = blockConsumer;
    }

    @Override
    public void setblock(int x, int y, int z, String block) {
      if (rotation != null) {
        int newX = rotation[0] * x + rotation[1] * y + rotation[2] * z;
        int newY = rotation[3] * x + rotation[4] * y + rotation[5] * z;
        int newZ = rotation[6] * x + rotation[7] * y + rotation[8] * z;
        x = newX;
        y = newY;
        z = newZ;
        block = reorientBlock(block, rotation);
      }
      if (offset != null) {
        x += offset[0];
        y += offset[1];
        z += offset[2];
      }
      blockConsumer.setblock(x, y, z, block);
    }

    @Override
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
      if (rotation != null) {
        int newX1 = rotation[0] * x1 + rotation[1] * y1 + rotation[2] * z1;
        int newY1 = rotation[3] * x1 + rotation[4] * y1 + rotation[5] * z1;
        int newZ1 = rotation[6] * x1 + rotation[7] * y1 + rotation[8] * z1;
        int newX2 = rotation[0] * x2 + rotation[1] * y2 + rotation[2] * z2;
        int newY2 = rotation[3] * x2 + rotation[4] * y2 + rotation[5] * z2;
        int newZ2 = rotation[6] * x2 + rotation[7] * y2 + rotation[8] * z2;
        if (newX1 < newX2) {
          x1 = newX1;
          x2 = newX2;
        } else {
          x2 = newX1;
          x1 = newX2;
        }
        if (newY1 < newY2) {
          y1 = newY1;
          y2 = newY2;
        } else {
          y2 = newY1;
          y1 = newY2;
        }
        if (newZ1 < newZ2) {
          z1 = newZ1;
          z2 = newZ2;
        } else {
          z2 = newZ1;
          z1 = newZ2;
        }
        block = reorientBlock(block, rotation);
      }
      if (offset != null) {
        x1 += offset[0];
        y1 += offset[1];
        z1 += offset[2];

        x2 += offset[0];
        y2 += offset[1];
        z2 += offset[2];
      }
      blockConsumer.fill(x1, y1, z1, x2, y2, z2, block);
    }
  }

  /**
   * Generates Minecraft commands (`/setblock` and `/fill`) for all blocks in the pack.
   *
   * @param rotation An optional 9-element rotation matrix to apply.
   * @param offset An optional 3-element offset to apply.
   * @param commandConsumer A consumer to receive the generated command strings.
   */
  // TODO(maxuser): Replace getBlockCommands with general-purpose iterability of tiles in layers
  // (see #Layering below).
  public void getBlockCommands(int rotation[], int[] offset, Consumer<String> commandConsumer) {
    getBlocks(
        new TransformedBlockConsumer(
            rotation,
            offset,
            new BlockConsumer() {
              @Override
              public void setblock(int x, int y, int z, String block) {
                commandConsumer.accept(String.format("setblock %d %d %d %s", x, y, z, block));
              }

              @Override
              public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
                commandConsumer.accept(
                    String.format("fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, block));
              }
            }));
  }

  /**
   * Iterates through all blocks in the pack, passing them to the provided {@link BlockConsumer}.
   *
   * @param blockConsumer The consumer for the block operations.
   */
  public void getBlocks(BlockConsumer blockConsumer) {
    for (Tile tile : tiles.values()) {
      // TODO(maxuser): Use tile.getBlockCommands(...) for the initial "stable blocks" layer, and
      // tile.getBlocksInAscendingYOrder(...) for the subsequent "unstable blocks" layer.
      tile.getBlocksInAscendingYOrder(symbolMap, blockConsumer);
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
  /** Represents a 32x32x32 chunk of blocks within a {@link BlockPack}. */
  public static class Tile {
    private final int xOffset;
    private final int yOffset;
    private final int zOffset;

    private final int[] blockTypes;

    // Every 3 shorts represents: (x1, y1, z1), (x2, y2, z2), block_type
    private final short[] fills;

    // Every 2 shorts represents: (x, y, z) and block_type
    private final short[] setblocks;

    /**
     * Constructs a new Tile.
     *
     * @param xOffset The world x-coordinate of the tile's origin.
     * @param yOffset The world y-coordinate of the tile's origin.
     * @param zOffset The world z-coordinate of the tile's origin.
     * @param blockTypes An array of block type IDs used within this tile.
     * @param fills An array of packed data for fill operations.
     * @param setblocks An array of packed data for setblock operations.
     */
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

    /**
     * Generates Minecraft commands for the blocks in this tile.
     *
     * @param setblockOnly If true, all fills will be expanded into individual setblock commands.
     * @param symbolMap The symbol map from the parent BlockPack.
     * @param commandConsumer A consumer for the generated command strings.
     */
    public void getBlockCommands(
        boolean setblockOnly, Map<Integer, BlockType> symbolMap, Consumer<String> commandConsumer) {
      int[] coord = new int[3];

      for (int i = 0; i < fills.length; i += 3) {
        getCoord(fills[i], coord);
        int x1 = xOffset + coord[0];
        int y1 = yOffset + coord[1];
        int z1 = zOffset + coord[2];

        getCoord(fills[i + 1], coord);
        int x2 = xOffset + coord[0];
        int y2 = yOffset + coord[1];
        int z2 = zOffset + coord[2];

        int blockTypeId = blockTypes[fills[i + 2]];
        BlockType blockType = symbolMap.get(blockTypeId);
        if (setblockOnly) {
          for (int x = x1; x <= x2; ++x) {
            for (int y = y1; y <= y2; ++y) {
              for (int z = z1; z <= z2; ++z) {
                commandConsumer.accept(
                    String.format("setblock %d %d %d %s", x, y, z, blockType.symbol));
              }
            }
          }
        } else {
          commandConsumer.accept(
              String.format("fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, blockType.symbol));
        }
      }
      for (int i = 0; i < setblocks.length; i += 2) {
        getCoord(setblocks[i], coord);
        int x = xOffset + coord[0];
        int y = yOffset + coord[1];
        int z = zOffset + coord[2];
        int blockTypeId = blockTypes[setblocks[i + 1]];
        BlockType blockType = symbolMap.get(blockTypeId);
        commandConsumer.accept(String.format("setblock %d %d %d %s", x, y, z, blockType.symbol));
      }
    }

    private static int[] getCoord(short s, int[] coord) {
      coord[0] = s >> 10;
      coord[1] = (s >> 5) & ((1 << 5) - 1);
      coord[2] = s & ((1 << 5) - 1);
      return coord;
    }

    /**
     * Iterates through the blocks in this tile in ascending Y-order, passing them to a {@link
     * BlockConsumer}.
     *
     * @param symbolMap The symbol map from the parent BlockPack.
     * @param blockConsumer The consumer for the block operations.
     */
    public void getBlocksInAscendingYOrder(
        Map<Integer, BlockType> symbolMap, BlockConsumer blockConsumer) {
      final int setblocksSize = setblocks.length;
      final int fillsSize = fills.length;

      int setblocksPos = 0;
      int fillsPos = 0;
      int[] coord = new int[3];
      while (setblocksPos < setblocksSize || fillsPos < fillsSize) {
        int fillsY = fillsPos < fillsSize ? getCoord(fills[fillsPos], coord)[1] : Integer.MAX_VALUE;
        int setblocksY =
            setblocksPos < setblocksSize
                ? getCoord(setblocks[setblocksPos], coord)[1]
                : Integer.MAX_VALUE;

        if (fillsY <= setblocksY) {
          // fill at fillsPos.
          int i = fillsPos;
          getCoord(fills[i], coord);
          int x1 = xOffset + coord[0];
          int y1 = yOffset + coord[1];
          int z1 = zOffset + coord[2];
          getCoord(fills[i + 1], coord);
          int x2 = xOffset + coord[0];
          int y2 = yOffset + coord[1];
          int z2 = zOffset + coord[2];
          int blockTypeId = blockTypes[fills[i + 2]];
          BlockType blockType = symbolMap.get(blockTypeId);
          blockConsumer.fill(x1, y1, z1, x2, y2, z2, blockType.symbol);
          fillsPos += 3;
        }

        if (setblocksY < fillsY) {
          // setblock at setblocksPos
          int i = setblocksPos;
          getCoord(setblocks[i], coord);
          int x = xOffset + coord[0];
          int y = yOffset + coord[1];
          int z = zOffset + coord[2];
          int blockTypeId = blockTypes[setblocks[i + 1]];
          BlockType blockType = symbolMap.get(blockTypeId);
          blockConsumer.setblock(x, y, z, blockType.symbol);
          setblocksPos += 2;
        }
      }
    }
  }
}
