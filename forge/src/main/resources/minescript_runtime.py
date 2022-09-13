# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""minescript_runtime v2.0.1 distributed via Minescript jar file

Usage: import minescript_runtime  # from Python script

Low-level interface and runtime for scripts to make function
calls into the Minescript mod. Most users should import
minescript.py instead for an API that is more user friendly.

CallScriptFunction(func_name):
  Makes a function call into the Minescript runtime, blocking
  execution until returning a value. The return value may be a
  string, numeric, or composite data such as a JSON array or
  structure.

CallAsyncScriptFunction(func_name, retval_handler):
  Makes a function call into the Minescript runtime, returning a
  value or stream of values asynchronously by invoking
  retval_handler(value), potentially multiple times. The number
  of times that the Minescript runtime calls
  retval_handler(value) is specific to each function.
"""

import json
import os
import re
import sys
import time
import threading
import traceback

from threading import Lock
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

StringConsumer = Callable[[str], None]
# Dict values: (function_name: str, on_value_handler: StringConsumer)
_script_function_calls: Dict[int, Tuple[str, StringConsumer]] = dict()


def CallAsyncScriptFunction(func_name: str, args: Tuple[Any, ...],
                            retval_handler: StringConsumer) -> None:
  """Calls a script function, asynchronously streaming return value(s).

  Args:
    func_name: name of Minescript function to call
    retval_handler: callback invoked for each return value
  """
  func_call_id = time.time_ns()
  _script_function_calls[func_call_id] = (func_name, retval_handler)
  print(f"?{func_call_id} {func_name} {json.dumps(args)}")


def CallScriptFunction(func_name: str, *args: Any) -> Any:
  """Calls a script function and returns the function's return value.

  Args:
    func_name: name of Minescript function to call

  Returns:
    script function's return value: number, string, list, or dict
  """
  retval_holder: List[Any] = []
  lock = Lock()
  lock.acquire()

  def WaitForReturnValue(retval: Any) -> None:
    retval_holder.append(retval)
    lock.release()

  CallAsyncScriptFunction(func_name, args, WaitForReturnValue)
  lock.acquire()
  return retval_holder[0]


def _ScriptServiceLoop():
  while True:
    try:
      json_input = input()
      reply = json.loads(json_input)
    except json.decoder.JSONDecodeError as e:
      traceback.print_exc(file=sys.stderr)
      print(f"JSON error in: {json_input}", file=sys.stderr)
      continue

    if "fcid" not in reply:
      print(
          "minescript_runtime.py: 'fcid' field missing in script function response",
          file=sys.stderr)
      continue
    func_call_id = reply["fcid"]

    # fcid zero is reserved for system management like exiting the program.
    if func_call_id == 0:
      if "retval" in reply:
        retval = reply["retval"]
        if retval == "exit!":
          break  # Break out of the service loop so that the process can exit.

    if func_call_id not in _script_function_calls:
      print(
          f"minescript_runtime.py: fcid={func_call_id} not found in _script_function_calls",
          file=sys.stderr)
      continue
    func_name, retval_handler = _script_function_calls[func_call_id]

    if "conn" in reply and reply["conn"] == "close":
      del _script_function_calls[func_call_id]

    if "retval" in reply:
      retval = reply["retval"]
      retval_handler(retval)

    if "conn" not in reply and "retval" not in reply:
      print(
          f"minescript_runtime.py: script function response has neither 'conn' nor 'retval': {reply}",
          file=sys.stderr)


def _WatchdogLoop():
  # Thread.isAlive() was renamed to Thread.is_alive() in Python 3.9.
  major, minor = sys.version_info[:2]
  if major != 3:
    print(f"Expected Python 3.x but got {major}.{minor}", file=sys.stderr)
    return
  if minor >= 9:
    is_alive = threading.main_thread().is_alive
  else:
    is_alive = threading.main_thread().isAlive

  while is_alive():
    time.sleep(0.2)

  print(f"?0 exit!")  # special pseudo-function for requesting script termination


_script_service_thread = threading.Thread(target=_ScriptServiceLoop,
                                          daemon=False)
_script_service_thread.start()

_watchdog_thread = threading.Thread(target=_WatchdogLoop, daemon=False)
_watchdog_thread.start()


def ReadDocString(filename):
  try:
    script = open(filename)
  except FileNotFoundError as e:
    print(f'Script "{filename}" not found.', file=sys.stderr)
    return None

  nlines = 0
  src = ""
  docstr_start_quote = None
  while nlines < 100:
    nlines += 1
    line = script.readline()
    if not line:
      break
    if docstr_start_quote is None:
      if not line.strip() or line.startswith("#"):
        continue
      if line[:3] in ('"""', "'''"):
        docstr_start_quote = line[:3]
      elif line[:4] in ('r"""', "r'''"):
        docstr_start_quote = line[1:4]
      else:
        break
    src += line
    if line.rstrip().endswith(docstr_start_quote):
      return eval(src)
  return None


_version_re = re.compile(r"v([0-9.]*[0-9])")

def ParseVersionTuple(version_str):
  """Parses a version string as a tuple of ints (otherwise None), e.g. "v1.2" -> (1, 2)"""
  if not version_str or type(version_str) is not str:
    return None

  re_match = _version_re.match(version_str)
  if not re_match:
    return None

  return [int(x) for x in re_match.group(1).split(".")]


def VersionAsString(version_tuple):
  """Prints a tuple of ints as a string, e.g. (1, 2, 3) -> 'v1.2.3'"""
  return "v" + ".".join([str(x) for x in version_tuple])


def CheckVersionCompatibility(
    module_name, module_docstr, errors=[], module_versions=dict(), debug=False):
  """Checks version compatibility of the given module, recursively parsing required deps.

  Args:
    module_name: name of the module to check compatibility
    module_docstr: docstring of module for checking versions of required deps
    errors: list to which error strings are added
    module_versions: cache of dict from module name (str) to version (tuple[int])
    debug: if True, print debug information (bool)

  Returns:
    version tuple (tuple of ints) of the given module
  """
  if not module_docstr:
    return None
  found_requires = False
  first_line = True
  declared_version = None
  for line in module_docstr.splitlines():
    line = line.strip()
    if first_line:
      first_line = False
      words = line.split()
      if len(words) >= 2:
        if module_name != words[0]:
          found = " ".join(line.split()[:2])
          errors.append(
              f'{module_name}: non-conforming docstring; '
              f'expected first line to start with "{module_name} v<version>" '
              f'but found "{found}..."')
        declared_version = ParseVersionTuple(words[1])
        if debug:
          version_str = VersionAsString(declared_version)
          print(
              f"(debug) Parsed module version from docstring: "
              f"{module_name} {version_str}",
              file=sys.stderr)
      else:
        errors.append(
            f'{module_name}: non-conforming docstring; '
            f'expected first line to start with "{module_name} v<version>" '
            f'but found "{line}"')
    if line == "Requires:":
      found_requires = True
    elif found_requires:
      if not line:
        break
      required_version_words = line.split()
      if len(required_version_words) == 2:
        name, required_version_str = required_version_words
        required_version = ParseVersionTuple(required_version_str)
        if not required_version:
          errors.append(
              f'{module_name}: cannot parse required version of {name}: '
              f'{required_version_str}')
          continue

        if name not in module_versions:
          script_filename = os.path.join("minescript", name) + ".py"
          if os.path.exists(script_filename):
            docstr = ReadDocString(script_filename)
            actual_version = CheckVersionCompatibility(
                name, docstr, errors, module_versions, debug)
            module_versions[name] = actual_version
          else:
            errors.append(
                f'{module_name}: missing required dependency: '
                f'{name} {VersionAsString(required_version)}')
            continue
        else:
          actual_version = module_versions[name]
          if debug:
            version_str = VersionAsString(actual_version)
            print(
                f'(debug) module verison previously computed: {name} {version_str}',
                file=sys.stderr)

        if not actual_version:
          errors.append(
              f'{module_name}: could not verify version of {name} '
              f'({module_name} requires {name} {VersionAsString(required_version)})')
        elif actual_version < required_version:
          errors.append(
              f'{module_name}: requires {name} {VersionAsString(required_version)} '
              f'but found {VersionAsString(actual_version)}')
      else:
        errors.append(
            f'{module_name}: cannot parse required version for dependency: "{line}"')

  return declared_version


def CheckMainModuleVersionCompatibility():
  debug = "--debug" in sys.argv
  main_module = sys.modules["__main__"]
  script_fullname = sys.argv[0]
  script_shortname = os.path.split(script_fullname)[-1].split(".py")[0]
  versioned_first_line_re = re.compile(r"([^ ]+) +(v[0-9.]+)")
  if main_module.__doc__:
    re_match = versioned_first_line_re.match(main_module.__doc__)
    if re_match and re_match.group(1) == script_shortname:
      errors = []
      CheckVersionCompatibility(
          script_shortname, main_module.__doc__, errors=errors, debug=debug)
      if errors:
        print(
            f"Warning: {script_shortname} failed version compatibility check:",
            file=sys.stderr)
        # If there's more than one error, print a numbered prefix before each,
        # e.g. "[1]".
        for i, error in enumerate(sorted(errors)):
          error_prefix = "  " if len(errors) == 1 else f"[{i+1}] "
          print(error_prefix + error, file=sys.stderr)
    elif debug:
      print(
          f'(debug) {script_shortname} has docstring without version header: '
          f'"{main_module.__doc__.splitlines()[0]}"',
          file=sys.stderr)
  elif debug:
    print(
        f'(debug) {script_shortname} has no docstring for checking version requirements.',
        file=sys.stderr)


CheckMainModuleVersionCompatibility()
