// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

public class Numbers {
  private Numbers() {}

  public static Number add(Number x, Number y) {
    if (x instanceof Double d) {
      return d + y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() + d;
    } else if (x instanceof Float f) {
      return f + y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() + f;
    } else if (x instanceof Long l) {
      return l + y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() + l;
    } else if (x instanceof Integer i) {
      return i + y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() + i;
    } else if (x instanceof Short s) {
      return s + y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() + s;
    } else if (x instanceof Byte b) {
      return b + y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() + b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to add numbers: %s + %s (%s + %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static Number subtract(Number x, Number y) {
    if (x instanceof Double d) {
      return d - y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() - d;
    } else if (x instanceof Float f) {
      return f - y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() - f;
    } else if (x instanceof Long l) {
      return l - y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() - l;
    } else if (x instanceof Integer i) {
      return i - y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() - i;
    } else if (x instanceof Short s) {
      return s - y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() - s;
    } else if (x instanceof Byte b) {
      return b - y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() - b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to subtract numbers: %s - %s (%s - %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static Number multiply(Number x, Number y) {
    if (x instanceof Double d) {
      return d * y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() * d;
    } else if (x instanceof Float f) {
      return f * y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() * f;
    } else if (x instanceof Long l) {
      return l * y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() * l;
    } else if (x instanceof Integer i) {
      return i * y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() * i;
    } else if (x instanceof Short s) {
      return s * y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() * s;
    } else if (x instanceof Byte b) {
      return b * y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() * b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to multiply numbers: %s * %s (%s * %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static Number divide(Number x, Number y) {
    if (x instanceof Double d) {
      return d / y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() / d;
    } else if (x instanceof Float f) {
      return f / y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() / f;
    } else if (x instanceof Long l) {
      return l / y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() / l;
    } else if (x instanceof Integer i) {
      return i / y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() / i;
    } else if (x instanceof Short s) {
      return s / y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() / s;
    } else if (x instanceof Byte b) {
      return b / y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() / b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to divide numbers: %s / %s (%s / %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static Number negate(Number x) {
    if (x instanceof Double d) {
      return -d;
    } else if (x instanceof Float f) {
      return -f;
    } else if (x instanceof Long l) {
      return -l;
    } else if (x instanceof Integer i) {
      return -i;
    } else if (x instanceof Short s) {
      return -s;
    } else if (x instanceof Byte b) {
      return -b;
    } else {
      throw new IllegalArgumentException(
          String.format("Unable to negate number: %s (%s)", x, x.getClass().getName()));
    }
  }

  public static boolean lessThan(Number x, Number y) {
    if (x instanceof Double d) {
      return d < y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() < d;
    } else if (x instanceof Float f) {
      return f < y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() < f;
    } else if (x instanceof Long l) {
      return l < y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() < l;
    } else if (x instanceof Integer i) {
      return i < y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() < i;
    } else if (x instanceof Short s) {
      return s < y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() < s;
    } else if (x instanceof Byte b) {
      return b < y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() < b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to compare numbers: %s < %s (%s < %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static boolean lessThanOrEquals(Number x, Number y) {
    if (x instanceof Double d) {
      return d <= y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() <= d;
    } else if (x instanceof Float f) {
      return f <= y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() <= f;
    } else if (x instanceof Long l) {
      return l <= y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() <= l;
    } else if (x instanceof Integer i) {
      return i <= y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() <= i;
    } else if (x instanceof Short s) {
      return s <= y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() <= s;
    } else if (x instanceof Byte b) {
      return b <= y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() <= b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to compare numbers: %s <= %s (%s <= %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static boolean equals(Number x, Number y) {
    if (x instanceof Double d) {
      return d == y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() == d;
    } else if (x instanceof Float f) {
      return f == y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() == f;
    } else if (x instanceof Long l) {
      return l == y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() == l;
    } else if (x instanceof Integer i) {
      return i == y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() == i;
    } else if (x instanceof Short s) {
      return s == y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() == s;
    } else if (x instanceof Byte b) {
      return b == y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() == b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to compare numbers: %s == %s (%s == %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static boolean greaterThanOrEquals(Number x, Number y) {
    if (x instanceof Double d) {
      return d >= y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() >= d;
    } else if (x instanceof Float f) {
      return f >= y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() >= f;
    } else if (x instanceof Long l) {
      return l >= y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() >= l;
    } else if (x instanceof Integer i) {
      return i >= y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() >= i;
    } else if (x instanceof Short s) {
      return s >= y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() >= s;
    } else if (x instanceof Byte b) {
      return b >= y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() >= b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to compare numbers: %s >= %s (%s >= %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }

  public static boolean greaterThan(Number x, Number y) {
    if (x instanceof Double d) {
      return d > y.doubleValue();
    } else if (y instanceof Double d) {
      return x.doubleValue() > d;
    } else if (x instanceof Float f) {
      return f > y.floatValue();
    } else if (y instanceof Float f) {
      return x.floatValue() > f;
    } else if (x instanceof Long l) {
      return l > y.longValue();
    } else if (y instanceof Long l) {
      return x.longValue() > l;
    } else if (x instanceof Integer i) {
      return i > y.intValue();
    } else if (y instanceof Integer i) {
      return x.intValue() > i;
    } else if (x instanceof Short s) {
      return s > y.shortValue();
    } else if (y instanceof Short s) {
      return x.shortValue() > s;
    } else if (x instanceof Byte b) {
      return b > y.byteValue();
    } else if (y instanceof Byte b) {
      return x.byteValue() > b;
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unable to compare numbers: %s > %s (%s > %s)",
              x, y, x.getClass().getName(), y.getClass().getName()));
    }
  }
}
