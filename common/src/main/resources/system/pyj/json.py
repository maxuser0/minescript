# SPDX-FileCopyrightText: © 2024-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""json v5.0 distributed via Minescript jar file

This library provides a subset of the functionality of the Python json module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

_Array = JavaClass("java.lang.reflect.Array")
_JsonParser = JavaClass("com.google.gson.JsonParser")

def loads(data: str) -> any:
  element = _JsonParser.parseString(data)
  return _from_json(element)

def _from_json(element):
  if element.isJsonNull():
    return None
  elif element.isJsonPrimitive():
    primitive = element.getAsJsonPrimitive()
    if primitive.isNumber():
      return primitive.getAsNumber()
    elif primitive.isString():
      return primitive.getAsString()
    elif primitive.isBoolean():
      return primitive.getAsBoolean()
    else:
      raise Exception(f"Unknown JSON primitive type: {primitive}")
  elif element.isJsonArray():
    array = element.getAsJsonArray()
    result = []
    for i in range(array.size()):
      result.append(_from_json(array.get(i)))
    return result
  elif element.isJsonObject():
    obj = element.getAsJsonObject()
    result = {}
    for entry in obj.entrySet():
      key = entry.getKey()
      value = entry.getValue()
      result[key] = _from_json(value)
    return result
  else:
    raise Exception(f"Unknown JSON element type: {element}")