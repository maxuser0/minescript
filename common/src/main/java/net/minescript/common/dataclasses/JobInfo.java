// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minescript.common.Jsonable;

public class JobInfo extends Jsonable {
  public int job_id;
  public String[] command;
  public String source;
  public String status;
  public Boolean self;

  public JobInfo(int job_id, String[] command, String source, String status, Boolean self) {
    this.job_id = job_id;
    this.command = command;
    this.source = source;
    this.status = status;
    this.self = self;
  }

  // Use default-constructed Gson (instead of GSON) so that nulls are not serialized.
  @Override
  public JsonElement toJson() {
    return new Gson().toJsonTree(this);
  }
}
