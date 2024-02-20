# SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""minescript_runtime v4.0 distributed via Minescript jar file

Usage: import minescript_runtime  # from Python script

Low-level interface and runtime for scripts to make function
calls into the Minescript mod. Most users should import
minescript.py instead for an API that is more user friendly.
"""

import asyncio
import concurrent.futures
import json
import os
import re
import sys
import time
import threading
import traceback
import _thread

from dataclasses import dataclass
from typing import Any, List, Set, Dict, Tuple, Optional, Callable, Awaitable

AnyConsumer = Callable[[str], None]
ExceptionHandler = Callable[[Exception], None]

# Dict values: (function_name: str, on_value_handler: AnyConsumer)
_script_function_calls: Dict[int, Tuple[str, AnyConsumer, ExceptionHandler]] = dict()
_script_function_calls_lock = threading.Lock()
_next_fcallid = 1000
_thread_pool = concurrent.futures.ThreadPoolExecutor()  # ThreadPool for coroutines

# Special prefix for emitting function calls, e.g. "?mnsc:123 my_func [4, 5, 6]"
_FUNCTION_PREFIX = "?mnsc:"


def call_noreturn_function(command: str, args):
  """Calls a function which does not return.

  Make a fire-and-forget function call with a call id of 0 and no return value.
  """
  with _script_function_calls_lock:
    print(f"{_FUNCTION_PREFIX}0 {command} {json.dumps(args)}")


def send_script_function_request(func_name: str, args: Tuple[Any, ...],
                                 retval_handler: AnyConsumer,
                                 exception_handler: ExceptionHandler = None) -> int:
  """Sends a request for a script function, asynchronously streaming return value(s) or exception.

  If `exception_handler` is invoked, `retval_handler` will no longer be called.

  Args:
    func_name: name of Minescript function to call
    retval_handler: callback invoked for each return value
    exception_handler: callback invoked if an exception is raised

  Returns:
    function call ID
  """
  global _next_fcallid
  with _script_function_calls_lock:
    _next_fcallid += 1
    func_call_id = _next_fcallid
    _script_function_calls[func_call_id] = (func_name, retval_handler, exception_handler)
    print(f"{_FUNCTION_PREFIX}{func_call_id} {func_name} {json.dumps(args)}")
  return func_call_id


async def call_async_script_function(func_name: str, args: Tuple[Any, ...]) -> Awaitable[Any]:
  """Calls a script function and awaits the function's return value.

  Args:
    func_name: name of Minescript function to call

  Returns:
    script function's return value: number, string, list, or dict
  """
  retval_holder: List[Any] = []
  exception_holder: List[Exception] = []
  lock = threading.Lock()
  lock.acquire()

  def HandleReturnValue(retval: Any) -> None:
    retval_holder.append(retval)
    lock.release()

  def HandleException(exception: Exception) -> None:
    exception_holder.append(exception)
    lock.release()

  send_script_function_request(func_name, args, HandleReturnValue, HandleException)

  loop = asyncio.get_event_loop()
  await loop.run_in_executor(_thread_pool, lock.acquire)

  if exception_holder:
    raise exception_holder[0]
  if retval_holder:
    return retval_holder[0]


def await_script_function(func_name: str, args: Tuple[Any, ...], timeout: float = None) -> Any:
  """Calls a script function and returns the function's return value.

  Args:
    func_name: name of Minescript function to call
    timeout: if specified, timeout in seconds to wait for the script function to complete

  Returns:
    script function's return value: number, string, list, or dict
  """
  retval_holder: List[Any] = []
  exception_holder: List[Exception] = []
  lock = threading.Lock()
  lock.acquire()

  def HandleReturnValue(retval: Any) -> None:
    retval_holder.append(retval)
    lock.release()

  def HandleException(exception: Exception) -> None:
    exception_holder.append(exception)
    lock.release()

  func_call_id = send_script_function_request(func_name, args, HandleReturnValue, HandleException)
  locked = lock.acquire(timeout=(-1 if timeout is None else timeout))
  if not locked:
    # Special pseudo-function for cancelling the function call.
    cancel_args = (func_call_id, func_name)
    call_noreturn_function("cancelfn!", cancel_args)
    raise TimeoutError(f'Timeout after {timeout} seconds')

  if exception_holder:
    raise exception_holder[0]
  if retval_holder:
    return retval_holder[0]


@dataclass
class StackElement:
  file: str
  method: str
  line: int

@dataclass
class JavaException(Exception):
  type: str
  message: str
  desc: str
  stack: List[StackElement]

  def __str__(self):
    return self.desc


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
      continue

    with _script_function_calls_lock:
      if func_call_id not in _script_function_calls:
        print(
            f"minescript_runtime.py: fcid={func_call_id} not found in _script_function_calls",
            file=sys.stderr)
        continue
      func_name, retval_handler, exception_handler = _script_function_calls[func_call_id]

      if "conn" in reply and reply["conn"] == "close":
        del _script_function_calls[func_call_id]

    if "except" in reply:
      e = reply["except"]
      exception = JavaException(
          e["type"], e["message"], e["desc"],
          [StackElement(s["file"], s["method"], s["line"]) for s in e["stack"]])
      if exception_handler is None:
        print(f"JavaException raised in `{func_name}`: {exception}", file=sys.stderr)
      else:
        exception_handler(exception)
    elif "retval" in reply:
      retval = reply["retval"]
      retval_handler(retval)

    if "conn" not in reply and "retval" not in reply and "except" not in reply:
      print(
          f"minescript_runtime.py: script function response missing 'conn', 'retval', and 'except': {reply}",
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

  # Request script termination.
  call_noreturn_function("exit!", ())


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


def ResolveScriptName(name):
  python_dirs = os.environ["PYTHONPATH"].split(os.pathsep)
  for dirname in python_dirs:
    script_filename = os.path.join(dirname, name) + ".py"
    if os.path.exists(script_filename):
      return script_filename
  return None


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
          script_filename = ResolveScriptName(name)
          if script_filename:
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
  debug = False
  if "--debug-version-check" in sys.argv:
    debug = True
    sys.argv.remove("--debug-version-check")

  relax_version_check = False
  if "--relax-version-check" in sys.argv:
    relax_version_check = True
    sys.argv.remove("--relax-version-check")

  version_check_only = False
  if "--version-check-only" in sys.argv:
    version_check_only = True
    sys.argv.remove("--version-check-only")

  main_module = sys.modules["__main__"]
  script_fullname = sys.argv[0]
  script_shortname = os.path.split(script_fullname)[-1].split(".py")[0]
  versioned_first_line_re = re.compile(r"([^ ]+) +(v[0-9.]+)")
  errors = []
  if main_module.__doc__:
    re_match = versioned_first_line_re.match(main_module.__doc__)
    if re_match and re_match.group(1) == script_shortname:
      CheckVersionCompatibility(
          script_shortname, main_module.__doc__, errors=errors, debug=debug)
      if errors:
        severity = "Warning" if relax_version_check else "Error"
        print(
            f"{severity}: {script_shortname} failed version compatibility check:",
            file=sys.stderr)
        # If there's more than one error, print a numbered prefix before each,
        # e.g. "[1]".
        for i, error in enumerate(sorted(errors)):
          error_prefix = "  " if len(errors) == 1 else f"[{i+1}] "
          print(error_prefix + error, file=sys.stderr)
        if not relax_version_check:
          print(
              "(Re-run with `--relax-version-check` to run anyway.)",
              file=sys.stderr)
          sys.exit(1)
    elif debug:
      print(
          f'(debug) {script_shortname} has docstring without version header: '
          f'"{main_module.__doc__.splitlines()[0]}"',
          file=sys.stderr)
  elif debug:
    print(
        f'(debug) {script_shortname} has no docstring for checking version requirements.',
        file=sys.stderr)

  if version_check_only:
    if not errors:
      print(
          f"Success: {script_shortname} passes version compatibility check.",
          file=sys.stderr)
    sys.exit()


CheckMainModuleVersionCompatibility()
