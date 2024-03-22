// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Accessors and precondition enforcement for an argument list to a script function. */
public class ScriptFunctionArgList {
  private String functionName;
  private List<?> args;
  private String argsString;
  private String[] expectedArgsNames = null;

  public ScriptFunctionArgList(String functionName, List<?> args, String argsString) {
    this.functionName = functionName;
    this.args = args;
    this.argsString = argsString;
  }

  public boolean isEmpty() {
    return args.isEmpty();
  }

  public int size() {
    return args.size();
  }

  public List<?> args() {
    return args;
  }

  public Object get(int argPos) {
    return args.get(argPos);
  }

  public void expectSize(int expectedArgs) {
    if (args.size() != expectedArgs) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected %d arg%s but got: %s",
              functionName, expectedArgs, expectedArgs == 1 ? "" : "s", argsString));
    }
  }

  public void expectArgs(String... argNames) {
    expectSize(argNames.length);
    expectedArgsNames = argNames;
  }

  public boolean getBoolean(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof Boolean value)) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be bool but got: %s", functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be bool but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }
    return value;
  }

  public int getStrictInt(int argPos) {
    var object = args.get(argPos);
    var value = getStrictIntValue(object);
    if (value.isEmpty()) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be int but got: %s", functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be int but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }
    return value.getAsInt();
  }

  public OptionalInt getOptionalStrictInt(int argPos) {
    return args.get(argPos) == null ? OptionalInt.empty() : OptionalInt.of(getStrictInt(argPos));
  }

  /** Returns int if arg is a Number convertible to int, possibly with truncation or rounding. */
  public int getConvertibleInt(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof Number number)) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be convertible to int but got: %s",
                  functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be convertible to int but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }
    return number.intValue();
  }

  public double getDouble(int argPos) {
    var object = args.get(argPos);
    OptionalDouble optional = getDoubleValue(object);
    if (optional.isEmpty()) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be float but got: %s", functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be float but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }
    return optional.getAsDouble();
  }

  public OptionalDouble getOptionalDouble(int argPos) {
    return args.get(argPos) == null ? OptionalDouble.empty() : OptionalDouble.of(getDouble(argPos));
  }

  public String getString(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof String)) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be string but got: %s", functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be string but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }
    return (String) object;
  }

  public Optional<String> getOptionalString(int argPos) {
    return args.get(argPos) == null ? Optional.empty() : Optional.of(getString(argPos));
  }

  public List<Integer> getIntListWithSize(int argPos, int expectedSize) {
    var object = args.get(argPos);
    if (!(object instanceof List<?> list) || list.size() != expectedSize) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be list of %d ints but got: %s",
                  functionName, argPos + 1, expectedSize, object)
              : String.format(
                  "`%s` expected %s to be list of %d ints but got: %s",
                  functionName, expectedArgsNames[argPos], expectedSize, object));
    }
    List<Integer> intList = new ArrayList<>();
    for (var element : list) {
      var asInt = getStrictIntValue(element);
      if (asInt.isEmpty()) {
        throw new IllegalArgumentException(
            expectedArgsNames == null
                ? String.format(
                    "`%s` expected arg %d to be list of %d ints but got: %s",
                    functionName, argPos + 1, expectedSize, object)
                : String.format(
                    "`%s` expected %s to be list of %d ints but got: %s",
                    functionName, expectedArgsNames[argPos], expectedSize, object));
      }
      intList.add(asInt.getAsInt());
    }
    return intList;
  }

  public Optional<List<Integer>> getOptionalIntListWithSize(int argPos, int expectedSize) {
    return args.get(argPos) == null
        ? Optional.empty()
        : Optional.of(getIntListWithSize(argPos, expectedSize));
  }

  public List<Double> getDoubleListWithSize(int argPos, int expectedSize) {
    var object = args.get(argPos);
    if (!(object instanceof List<?> list) || list.size() != expectedSize) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be list of %d floats but got: %s",
                  functionName, argPos + 1, expectedSize, object)
              : String.format(
                  "`%s` expected %s to be list of %d floats but got: %s",
                  functionName, expectedArgsNames[argPos], expectedSize, object));
    }
    List<Double> intList = new ArrayList<>();
    for (var element : list) {
      var asDouble = getDoubleValue(element);
      if (asDouble.isEmpty()) {
        throw new IllegalArgumentException(
            expectedArgsNames == null
                ? String.format(
                    "`%s` expected arg %d to be list of %d floats but got: %s",
                    functionName, argPos + 1, expectedSize, object)
                : String.format(
                    "`%s` expected %s to be list of %d floats but got: %s",
                    functionName, expectedArgsNames[argPos], expectedSize, object));
      }
      intList.add(asDouble.getAsDouble());
    }
    return intList;
  }

  public Optional<List<Double>> getOptionalDoubleListWithSize(int argPos, int expectedSize) {
    return args.get(argPos) == null
        ? Optional.empty()
        : Optional.of(getDoubleListWithSize(argPos, expectedSize));
  }

  /** Converts arg at argPos to a list of strings, using .toString() on elements as needed. */
  public List<String> getConvertibleStringList(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof List<?> list)) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be list of strings but got: %s",
                  functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be list of strings but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }

    List<String> stringList = new ArrayList<>();
    for (var element : list) {
      stringList.add(element.toString());
    }
    return stringList;
  }

  /** Converts arg at argPos to a map of strings, using .toString() on elements as needed. */
  public Map<String, String> getConvertibleStringMap(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(
          expectedArgsNames == null
              ? String.format(
                  "`%s` expected arg %d to be string map but got: %s",
                  functionName, argPos + 1, object)
              : String.format(
                  "`%s` expected %s to be string map but got: %s",
                  functionName, expectedArgsNames[argPos], object));
    }
    Map<String, String> stringMap = new HashMap<>();
    for (var entry : map.entrySet()) {
      stringMap.put(entry.getKey().toString(), entry.getValue().toString());
    }
    return stringMap;
  }

  private static OptionalDouble getDoubleValue(Object object) {
    if (object instanceof Number) {
      Number number = (Number) object;
      if (number instanceof Double) {
        return OptionalDouble.of(number.doubleValue());
      }
      if (number instanceof Float) {
        return OptionalDouble.of(number.doubleValue());
      }
      if (number instanceof Long) {
        return OptionalDouble.of(number.doubleValue());
      }
      if (number instanceof Integer) {
        return OptionalDouble.of(number.doubleValue());
      }
    }
    return OptionalDouble.empty();
  }

  /** Returns int if object is a Number representing an int without truncation or rounding. */
  public static OptionalInt getStrictIntValue(Object object) {
    if (!(object instanceof Number)) {
      return OptionalInt.empty();
    }
    Number number = (Number) object;
    if (number instanceof Integer) {
      return OptionalInt.of(number.intValue());
    }
    if (number instanceof Long) {
      long lng = number.longValue();
      if (lng >= Integer.MIN_VALUE && lng <= Integer.MAX_VALUE) {
        return OptionalInt.of(number.intValue());
      } else {
        return OptionalInt.empty();
      }
    }
    if (number instanceof Double) {
      double dbl = number.doubleValue();
      if (!Double.isInfinite(dbl) && dbl == Math.floor(dbl)) {
        return OptionalInt.of(number.intValue());
      }
    }
    return OptionalInt.empty();
  }

  /** Returns long if object is a Number representing a long without truncation or rounding. */
  public static OptionalLong getStrictLongValue(Object object) {
    if (!(object instanceof Number number)) {
      return OptionalLong.empty();
    }
    if (number instanceof Integer) {
      return OptionalLong.of(number.intValue());
    }
    if (number instanceof Long) {
      return OptionalLong.of(number.longValue());
    }
    if (number instanceof Double) {
      double dbl = number.doubleValue();
      if (!Double.isInfinite(dbl) && dbl == Math.floor(dbl)) {
        return OptionalLong.of(number.longValue());
      }
    }
    return OptionalLong.empty();
  }
}
