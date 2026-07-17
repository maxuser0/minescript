// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

class EventDispatcher {
  private Map<JobOperationId, EventListener> listeners = new ConcurrentHashMap<>();
  private Function<Map<String, Object>, Optional<Predicate<Object>>> listenerArgsProcessor;

  EventDispatcher() {
    this(args -> Optional.empty());
  }

  EventDispatcher(
      Function<Map<String, Object>, Optional<Predicate<Object>>> listenerArgsProcessor) {
    this.listenerArgsProcessor = listenerArgsProcessor;
  }

  boolean isEmpty() {
    return listeners.isEmpty();
  }

  Set<Map.Entry<JobOperationId, EventListener>> entrySet() {
    return listeners.entrySet();
  }

  Set<JobOperationId> keySet() {
    return listeners.keySet();
  }

  Collection<EventListener> values() {
    return listeners.values();
  }

  boolean containsKey(JobOperationId id) {
    return listeners.containsKey(id);
  }

  EventListener addListener(
      JobOperationId id, Map<String, Object> listenerArgs, EventListener listener) {
    initListener(listenerArgs, listener);
    return listeners.put(id, listener);
  }

  private void initListener(Map<String, Object> listenerArgs, EventListener listener) {
    listenerArgsProcessor.apply(listenerArgs).ifPresent(listener::setFilter);
  }

  EventListener get(JobOperationId id) {
    return listeners.get(id);
  }

  EventListener remove(JobOperationId id) {
    return listeners.remove(id);
  }
}
