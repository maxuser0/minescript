# SPDX-FileCopyrightText: © 2026 @SmartBoty <https://github.com/SmartBoty>
# SPDX-License-Identifier: GPL-3.0-only
# Original source: https://github.com/SmartBoty/Minescript/blob/main/pyjinn/pyjinn_json.py

"""json v6.0 distributed via Minescript jar file

Intended for the Pyjinn environment created by Greg Christiana <maxuser@minescript.net>.

This library provides a subset of the functionality of the Python json module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""


#!python
from system.pyj.minescript import JavaClass # juicy syntax coloring

ToNumberPolicy = JavaClass("com.google.gson.ToNumberPolicy")
Gson = JavaClass("com.google.gson.GsonBuilder")().serializeNulls().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create()
Map = JavaClass("java.util.Map")
HashMap = JavaClass("java.util.HashMap")
List = JavaClass("java.util.List")
ArrayList = JavaClass("java.util.ArrayList")
String = JavaClass("java.lang.String")
Long = JavaClass("java.lang.Long")

def _listify_pyjinnlist(items):
    lst = ArrayList()
    for item in items:
        lst.add(item)
    return lst

def _isdigit(item):
    if item.startswith("-"): item = item[1:]
    try: float(item) ; return True
    except: pass
    return False

def _mapify_pyjinndict(pyjinndict):
    out = HashMap()
    for key,value in pyjinndict.items():
        out.put(key,_handle_tojson(value))
    return out

def _dictify_javamap(javamap):
    out = {}
    for key in javamap.keySet():
        out[key] = _handle_fromjson(javamap.get(key))
    return out

def _handle_fromjson(obj):
    if isinstance(obj, Map): return _dictify_javamap(obj)
    elif isinstance(obj, List): return [_handle_fromjson(javaobj) for javaobj in obj]
    elif isinstance(obj, Long): return int(obj)
    else: return obj

def _handle_tojson(obj):
    if isinstance(obj, dict): return _mapify_pyjinndict(obj)
    elif isinstance(obj, (list, tuple)): return _listify_pyjinnlist([_handle_tojson(javaobj) for javaobj in obj])
    else: return obj

def loads(json_string:str):
    try:
        json_string = json_string.strip()
        if json_string.startswith("["): return _handle_fromjson(Gson.fromJson(json_string, type(List)))
        elif json_string.startswith("{"): return _handle_fromjson(Gson.fromJson(json_string, type(Map)))
        elif json_string == "null": return None
        elif json_string == "true": return True
        elif json_string == "false": return False
        elif _isdigit(json_string):
            if "." in json_string or "e" in json_string.lower(): return float(json_string)
            else: return int(json_string)
        elif json_string.startswith('"'):
            if json_string.endswith('"'): return Gson.fromJson(json_string, type(String))
            else: raise Exception(f"Unterminated string: {json_string}<<<")
        else: raise Exception(f"Invalid literal: '{json_string}'")
    except Exception as e: raise Exception(f"§eJSONDecodeException: {e.getMessage().replace("path $.","")}")

def dumps(obj) -> str:
    try: return Gson.toJson(_handle_tojson(obj))
    except Exception as e: raise Exception(f"§eJSONEncodeException: {e.getMessage()}")
