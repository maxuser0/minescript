// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.pyjinn;

import java.util.Arrays;
import org.pyjinn.interpreter.Script;
import org.pyjinn.interpreter.Script.PyList;
import org.pyjinn.interpreter.Script.PyTuple;

public class PyjinnUtil {

  public static final int[] toNullableIntArray(Object object) {
    if (object == null) {
      return null;
    }
    return toRequiredIntArray(object);
  }

  public static final int[] toRequiredIntArray(Object object) {
    if (object instanceof PyList pyList) {
      return Script.getJavaList(pyList).stream()
          .map(Number.class::cast)
          .mapToInt(Number::intValue)
          .toArray();
    } else if (object instanceof PyTuple pyTuple) {
      return Arrays.stream(Script.getJavaArray(pyTuple))
          .map(Number.class::cast)
          .mapToInt(Number::intValue)
          .toArray();
    } else if (object instanceof Object[] array) {
      return Arrays.stream(array).map(Number.class::cast).mapToInt(Number::intValue).toArray();
    } else if (object instanceof int[] array) {
      return array;
    } else {
      throw new IllegalArgumentException(
          "Unexpected type not convertible to int array: " + object.getClass());
    }
  }
}
