// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mappings;

import java.util.Set;

public class NoNameMappings implements NameMappings {
  @Override
  public String getRuntimeClassName(String prettyClassName) {
    return prettyClassName;
  }

  @Override
  public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
    return prettyFieldName;
  }

  @Override
  public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
    return Set.of(prettyMethodName);
  }
}
