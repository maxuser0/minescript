// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Tracker for managing resources accessed by a script job. */
public class ResourceTracker<T> {
  private static final Logger LOGGER = LogManager.getLogger();

  private final String resourceTypeName;
  private final int jobId;
  private final Config config;
  private final AtomicLong idAllocator = new AtomicLong(0);
  private final Map<Long, T> resources = new HashMap<>();

  public enum IdStatus {
    UNALLOCATED(0),
    ALLOCATED(1),
    RELEASED(2);

    private final int code;

    IdStatus(int code) {
      this.code = code;
    }

    public int code() {
      return code;
    }
  }

  public ResourceTracker(Class<T> resourceType, int jobId, Config config) {
    resourceTypeName = resourceType.getSimpleName();
    this.jobId = jobId;
    this.config = config;
  }

  // Assumes external synchronization on `this`.
  private IdStatus getIdStatus(long id) {
    if (id <= 0 || id > idAllocator.get()) {
      return IdStatus.UNALLOCATED;
    } else if (resources.containsKey(id)) {
      return IdStatus.ALLOCATED;
    } else {
      return IdStatus.RELEASED;
    }
  }

  public long retain(T resource) {
    long id = idAllocator.incrementAndGet();
    synchronized (this) {
      resources.put(id, resource);
    }
    if (config.debugOutput()) {
      LOGGER.info("Mapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
    }
    return id;
  }

  public void reassignId(long id, T resource) {
    synchronized (this) {
      switch (getIdStatus(id)) {
        case UNALLOCATED:
          throw new IllegalArgumentException(
              String.format("No %s allocated yet with ID %s", resourceTypeName, id));
        case RELEASED:
          throw new IllegalStateException(
              String.format("%s with ID %s already released", resourceTypeName, id));
        case ALLOCATED:
          resources.put(id, resource);
      }
    }
    if (config.debugOutput()) {
      LOGGER.info("Remapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
    }
  }

  public T getById(long id) {
    // Special-case zero as the ID for Java null reference so that it can be produced from scripts.
    // Note that zero as null does not preclude non-zero IDs from mapping to null.
    if (id == 0L) {
      return null;
    }
    synchronized (this) {
      switch (getIdStatus(id)) {
        case UNALLOCATED:
          throw new IllegalArgumentException(
              String.format("No %s allocated yet with ID %s", resourceTypeName, id));
        case RELEASED:
          throw new IllegalStateException(
              String.format("%s with ID %s already released", resourceTypeName, id));
        case ALLOCATED:
        default:
          return resources.get(id);
      }
    }
  }

  public T releaseById(long id) {
    T resource;
    synchronized (this) {
      switch (getIdStatus(id)) {
        case UNALLOCATED:
          throw new IllegalArgumentException(
              String.format("No %s allocated yet with ID %s", resourceTypeName, id));
        case RELEASED:
          throw new IllegalStateException(
              String.format("%s with ID %s already released", resourceTypeName, id));
        case ALLOCATED:
        default:
          resource = resources.remove(id);
      }
    }
    if (config.debugOutput()) {
      LOGGER.info("Unmapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
    }
    return resource;
  }

  public void releaseAll() {
    synchronized (this) {
      if (config.debugOutput()) {
        for (var entry : resources.entrySet()) {
          long id = entry.getKey();
          T resource = entry.getValue();
          LOGGER.info("Unmapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
        }
      }
      resources.clear();
    }
  }
}
