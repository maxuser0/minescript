// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mappings;

import java.util.Set;

public interface NameMappings {
  String getRuntimeClassName(String prettyClassName);

  String getPrettyClassName(String runtimeClassName);

  String getRuntimeFieldName(Class<?> clazz, String prettyFieldName);

  Set<String> getPrettyFieldNames(Class<?> clazz);

  Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName);

  Set<String> getPrettyMethodNames(Class<?> clazz);
}
