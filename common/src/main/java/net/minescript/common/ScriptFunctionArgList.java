// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Accessors and precondition enforcement for an argument list to a script function. */
public class ScriptFunctionArgList {
  private String functionName;
  private List<?> args;
  private String argsString;

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

  public boolean getBoolean(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof Boolean value)) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be bool but got: %s", functionName, argPos + 1, argsString));
    }
    return value;
  }

  public int getStrictInt(int argPos) {
    var value = getStrictIntValue(args.get(argPos));
    if (value.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be int but got: %s", functionName, argPos + 1, argsString));
    }
    return value.getAsInt();
  }

  /** Returns int if arg is a Number convertible to int, possibly with truncation or rounding. */
  public int getConvertibleInt(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof Number number)) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be convertible to int but got: %s",
              functionName, argPos + 1, argsString));
    }
    return number.intValue();
  }

  public Double getDouble(int argPos) {
    var value = getStrictDoubleValue(args.get(argPos));
    if (value.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be float but got: %s",
              functionName, argPos + 1, argsString));
    }
    return value.getAsDouble();
  }

  public String getString(int argPos) {
    var arg = args.get(argPos);
    if (!(arg instanceof String)) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be string but got: %s",
              functionName, argPos + 1, argsString));
    }
    return (String) arg;
  }

  public List<Integer> getIntListWithSize(int argPos, int expectedSize) {
    var object = args.get(argPos);
    if (!(object instanceof List<?> list)) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be list of %d ints but got: %s",
              functionName, argPos + 1, expectedSize, argsString));
    }
    List<Integer> intList = new ArrayList<>();
    for (var element : list) {
      var asInt = getStrictIntValue(element);
      if (asInt.isEmpty()) {
        throw new IllegalArgumentException(
            String.format(
                "`%s` expected arg %d to be list of %d ints but got: %s",
                functionName, argPos + 1, expectedSize, argsString));
      }
      intList.add(asInt.getAsInt());
    }
    return intList;
  }

  /** Converts arg at argPos to a list of strings, using .toString() on elements as needed. */
  public List<String> getConvertibleStringList(int argPos) {
    var object = args.get(argPos);
    if (!(object instanceof List<?> list)) {
      throw new IllegalArgumentException(
          String.format(
              "`%s` expected arg %d to be list of strings but got: %s",
              functionName, argPos + 1, argsString));
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
          String.format(
              "`%s` expected arg %d to be string map but got: %s",
              functionName, argPos + 1, argsString));
    }
    Map<String, String> stringMap = new HashMap<>();
    for (var entry : map.entrySet()) {
      stringMap.put(entry.getKey().toString(), entry.getValue().toString());
    }
    return stringMap;
  }

  /** Returns int if object is a Number representing an int without truncation or rounding. */
  private static OptionalInt getStrictIntValue(Object object) {
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

  private static OptionalDouble getStrictDoubleValue(Object object) {
    if (!(object instanceof Number)) {
      return OptionalDouble.empty();
    }
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
    return OptionalDouble.empty();
  }
}
