// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.JsonElement;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class EventListener implements JobControl.Operation {
  private static final Logger LOGGER = LogManager.getLogger();

  private final JobControl job;
  private final String name;
  private OptionalLong funcCallId = OptionalLong.empty();
  private State state = State.IDLE;
  private boolean suspended = false;
  private Runnable doneCallback;
  private Optional<Predicate<Object>> filter;

  public enum State {
    IDLE,
    ACTIVE,
    CANCELLED
  }

  public EventListener(JobControl job, String eventName, Runnable doneCallback) {
    this.job = job;
    this.name = eventName + "_listener";
    this.doneCallback = doneCallback;
  }

  public void setFilter(Predicate<Object> filter) {
    this.filter = Optional.of(filter);
  }

  public boolean applies(Object event) {
    if (filter.isPresent()) {
      return filter.get().test(event);
    }
    return true;
  }

  int jobId() {
    return job.jobId();
  }

  @Override
  public String name() {
    return name;
  }

  public synchronized void start(long funcCallId) {
    if (state != State.CANCELLED) {
      this.funcCallId = OptionalLong.of(funcCallId);
      state = State.ACTIVE;
    }
  }

  public synchronized boolean isActive() {
    return !suspended && state == State.ACTIVE;
  }

  @Override
  public synchronized void suspend() {
    suspended = true;
  }

  @Override
  public boolean resumeAndCheckDone() {
    if (state == State.CANCELLED) {
      return true;
    }
    suspended = false;
    return false;
  }

  @Override
  public synchronized void cancel() {
    LOGGER.info("Cancelling EventListener `{}` for job {} func {}", name(), jobId(), funcCallId);
    state = State.CANCELLED;
    funcCallId = OptionalLong.empty();
    doneCallback.run();
  }

  public synchronized boolean respond(JsonElement returnValue) {
    if (funcCallId.isPresent()) {
      return job.respond(funcCallId.getAsLong(), returnValue, /* finalReply= */ false);
    } else {
      return false;
    }
  }
}
