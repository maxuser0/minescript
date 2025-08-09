// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.pyjinn;

import java.util.Arrays;
import org.pyjinn.interpreter.Script.PyStreamable;

public class PyjinnUtil {

  public static final int[] toNullableIntArray(Object object) {
    if (object == null) {
      return null;
    }
    return toRequiredIntArray(object);
  }

  public static final int[] toRequiredIntArray(Object object) {
    if (object instanceof PyStreamable pyStreamable) {
      return pyStreamable.stream().map(Number.class::cast).mapToInt(Number::intValue).toArray();
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
