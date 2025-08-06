// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import java.util.concurrent.Callable;

public class ScriptValue {
  private final Object value;
  private final Callable<JsonElement> toJson;

  public static final ScriptValue TRUE = ScriptValue.of(true);
  public static final ScriptValue FALSE = ScriptValue.of(false);
  public static final ScriptValue NULL = new ScriptValue(null, () -> JsonNull.INSTANCE);

  private ScriptValue(Object value, Callable<JsonElement> toJson) {
    this.value = value;
    this.toJson = toJson;
  }

  public static ScriptValue of(Object value, Callable<JsonElement> toJson) {
    return new ScriptValue(value, toJson);
  }

  public static ScriptValue of(Boolean value) {
    return new ScriptValue(value, () -> new JsonPrimitive(value));
  }

  public static ScriptValue of(Number value) {
    return new ScriptValue(value, () -> new JsonPrimitive(value));
  }

  public static ScriptValue of(String value) {
    return new ScriptValue(value, () -> new JsonPrimitive(value));
  }

  public static ScriptValue of(Jsonable value) {
    return new ScriptValue(value, value::toJson);
  }

  public static <T> ScriptValue of(T[] value) {
    return new ScriptValue(value, () -> new Gson().toJsonTree(value));
  }

  public static ScriptValue fromJson(JsonElement value) {
    return new ScriptValue(value, () -> value);
  }

  public Object get() {
    return value;
  }

  @Override
  public String toString() {
    return toJson().toString();
  }

  /** Converts this value to JSON. */
  JsonElement toJson() {
    try {
      return toJson.call();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
