// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mappings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class NoNameMappings implements NameMappings {
  @Override
  public String getRuntimeClassName(String prettyClassName) {
    return prettyClassName;
  }

  @Override
  public String getPrettyClassName(String runtimeClassName) {
    return runtimeClassName;
  }

  @Override
  public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
    return prettyFieldName;
  }

  @Override
  public Set<String> getPrettyFieldNames(Class<?> clazz) {
    return Arrays.stream(clazz.getFields()).map(Field::getName).collect(Collectors.toSet());
  }

  @Override
  public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
    return Set.of(prettyMethodName);
  }

  @Override
  public Set<String> getPrettyMethodNames(Class<?> clazz) {
    return Arrays.stream(clazz.getMethods()).map(Method::getName).collect(Collectors.toSet());
  }
}
