// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Tracker for managing resources accessed by a script job. */
public class ResourceTracker<T> {
  private static final Logger LOGGER = LogManager.getLogger();

  private final String resourceTypeName;
  private final int jobId;
  private final Config config;
  private final AtomicInteger idAllocator = new AtomicInteger(0);
  private final Map<Integer, T> resources = new ConcurrentHashMap<>();

  public ResourceTracker(Class<T> resourceType, int jobId, Config config) {
    resourceTypeName = resourceType.getSimpleName();
    this.jobId = jobId;
    this.config = config;
  }

  public int retain(T resource) {
    int id = idAllocator.incrementAndGet();
    resources.put(id, resource);
    if (config.debugOutput()) {
      LOGGER.info("Mapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
    }
    return id;
  }

  public int reassignId(int id, T resource) {
    resources.put(id, resource);
    if (config.debugOutput()) {
      LOGGER.info("Remapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
    }
    return id;
  }

  public T getById(int id) {
    return resources.get(id);
  }

  public T releaseById(int id) {
    var resource = resources.remove(id);
    if (resource != null && config.debugOutput()) {
      LOGGER.info("Unmapped Job[{}] {}[{}]: {}", jobId, resourceTypeName, id, resource);
    }
    return resource;
  }

  public void releaseAll() {
    for (int id : resources.keySet()) {
      releaseById(id);
    }
  }
}
