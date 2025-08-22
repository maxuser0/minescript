// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minescript.common.Jsonable;

public class JobInfo extends Jsonable {
  public final int job_id;
  public final String[] command;
  public final String source;
  public final String status;
  public final Integer parent_job_id;
  public final Boolean self;

  public JobInfo(
      int job_id,
      String[] command,
      String source,
      String status,
      Integer parent_job_id,
      boolean self) {
    this.job_id = job_id;
    this.command = command;
    this.source = source;
    this.status = status;
    this.parent_job_id = parent_job_id;
    this.self = self;
  }

  @Override
  public JsonElement toJson() {
    return new GsonBuilder().serializeNulls().create().toJsonTree(this);
  }
}
