// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LevelAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkLoadEventListener implements Job.Operation {
  private static final Logger LOGGER = LogManager.getLogger();

  interface DoneCallback {
    void done(boolean success, boolean removeFromListeners);
  }

  // Map packed chunk (x, z) to boolean: true if chunk is loaded, false otherwise.
  private final Map<Long, Boolean> chunksToLoad = new ConcurrentHashMap<>();

  // Level with chunks to listen for. Store hash rather than reference to avoid memory leak.
  private final int levelHashCode;

  private final DoneCallback doneCallback;
  private int numUnloadedChunks = 0;
  private boolean suspended = false;
  private boolean finished = false;

  public ChunkLoadEventListener(int x1, int z1, int x2, int z2, DoneCallback doneCallback) {
    var minecraft = Minecraft.getInstance();
    this.levelHashCode = minecraft.level.hashCode();
    LOGGER.info("listener chunk region in level {}: {} {} {} {}", levelHashCode, x1, z1, x2, z2);
    int chunkX1 = worldCoordToChunkCoord(x1);
    int chunkZ1 = worldCoordToChunkCoord(z1);
    int chunkX2 = worldCoordToChunkCoord(x2);
    int chunkZ2 = worldCoordToChunkCoord(z2);

    int chunkXMin = Math.min(chunkX1, chunkX2);
    int chunkXMax = Math.max(chunkX1, chunkX2);
    int chunkZMin = Math.min(chunkZ1, chunkZ2);
    int chunkZMax = Math.max(chunkZ1, chunkZ2);

    for (int chunkX = chunkXMin; chunkX <= chunkXMax; chunkX++) {
      for (int chunkZ = chunkZMin; chunkZ <= chunkZMax; chunkZ++) {
        LOGGER.info("listener chunk registered: {} {}", chunkX, chunkZ);
        long packedChunkXZ = packInts(chunkX, chunkZ);
        chunksToLoad.put(packedChunkXZ, false);
      }
    }
    this.doneCallback = doneCallback;
  }

  @Override
  public String name() {
    return "chunk_load_listener";
  }

  @Override
  public synchronized void suspend() {
    suspended = true;
  }

  /** Resume this listener and return true if it's done. */
  @Override
  public synchronized boolean resumeAndCheckDone() {
    suspended = false;

    updateChunkStatuses();
    if (checkFullyLoaded()) {
      return true;
    }
    return false;
  }

  @Override
  public synchronized void cancel() {
    onFinished(/*success=*/ false, /*removeFromListeners=*/ true);
  }

  public synchronized void updateChunkStatuses() {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    if (level.hashCode() != this.levelHashCode) {
      LOGGER.info("chunk listener's world doesn't match current world; clearing listener");
      chunksToLoad.clear();
      numUnloadedChunks = 0;
      return;
    }
    numUnloadedChunks = 0;
    var chunkManager = level.getChunkSource();
    for (var entry : chunksToLoad.entrySet()) {
      long packedChunkXZ = entry.getKey();
      int[] chunkCoords = unpackLong(packedChunkXZ);
      boolean isLoaded = chunkManager.getChunkNow(chunkCoords[0], chunkCoords[1]) != null;
      entry.setValue(isLoaded);
      if (!isLoaded) {
        numUnloadedChunks++;
      }
    }
    LOGGER.info("Unloaded chunks after updateChunkStatuses: {}", numUnloadedChunks);
  }

  /** Returns true if the final outstanding chunk is loaded. */
  public synchronized boolean onChunkLoaded(LevelAccessor chunkLevel, int chunkX, int chunkZ) {
    if (suspended) {
      return false;
    }
    if (chunkLevel.hashCode() != levelHashCode) {
      return false;
    }
    long packedChunkXZ = packInts(chunkX, chunkZ);
    if (!chunksToLoad.containsKey(packedChunkXZ)) {
      return false;
    }
    boolean wasLoaded = chunksToLoad.put(packedChunkXZ, true);
    if (!wasLoaded) {
      LOGGER.info("listener chunk loaded for level {}: {} {}", levelHashCode, chunkX, chunkZ);
      numUnloadedChunks--;
      if (numUnloadedChunks == 0) {
        onFinished(/*success=*/ true, /*removeFromListeners=*/ false);
        return true;
      }
    }
    return false;
  }

  public synchronized void onChunkUnloaded(LevelAccessor chunkLevel, int chunkX, int chunkZ) {
    if (suspended) {
      return;
    }
    if (chunkLevel.hashCode() != levelHashCode) {
      return;
    }
    long packedChunkXZ = packInts(chunkX, chunkZ);
    if (!chunksToLoad.containsKey(packedChunkXZ)) {
      return;
    }
    boolean wasLoaded = chunksToLoad.put(packedChunkXZ, false);
    if (wasLoaded) {
      numUnloadedChunks++;
    }
  }

  public synchronized boolean checkFullyLoaded() {
    if (numUnloadedChunks == 0) {
      onFinished(/*success=*/ true, /*removeFromListeners=*/ true);
      return true;
    } else {
      return false;
    }
  }

  /** To be called when either all requested chunks are loaded or operation is cancelled. */
  private synchronized void onFinished(boolean success, boolean removeFromListeners) {
    if (finished) {
      LOGGER.warn(
          "ChunkLoadEventListener already finished; finished again with {}",
          success ? "success" : "failure");
      return;
    }
    finished = true;
    doneCallback.done(success, removeFromListeners);
  }

  private static int worldCoordToChunkCoord(int x) {
    return (x >= 0) ? (x / 16) : (((x + 1) / 16) - 1);
  }

  private static long packInts(int x, int z) {
    return (((long) x) << 32) | (z & 0xffffffffL);
  }

  /** Unpack 64-bit long into two 32-bit ints written to returned 2-element int array. */
  private static int[] unpackLong(long x) {
    return new int[] {(int) (x >> 32), (int) x};
  }
}
