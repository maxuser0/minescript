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

import json
import os
import re
import sys
import time
import threading
import traceback

from dataclasses import dataclass
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

AnyConsumer = Callable[[str], None]
ExceptionHandler = Callable[[Exception], None]

stdin_readline = sys.stdin.readline

def debug_log(*args):
  pass

_is_debug = os.environ.get("MINESCRIPT_DEBUG", False) not in (False, "0", "")
if _is_debug:
  import builtins

  _debug_log = open(os.path.join("minescript", "system", "minescript_debug.log"), "w")

  def debug_log(*args, **kwargs):
    builtins.print(*args, **kwargs, file=_debug_log)
    _debug_log.flush()

  def print(*args, **kwargs):
    if "file" not in kwargs:
      builtins.print("<", *args, **kwargs, file=_debug_log)
      _debug_log.flush()
    builtins.print(*args, **kwargs)

  def stdin_readline():
    builtins.print("Reading line from stdin...", file=_debug_log)
    _debug_log.flush()
    s = sys.stdin.readline()
    builtins.print(">", s, file=_debug_log)
    _debug_log.flush()
    return s

# Dict values: (function_name: str, on_value_handler: AnyConsumer)
_script_function_calls: Dict[int, Tuple[str, AnyConsumer, ExceptionHandler]] = dict()
_script_function_calls_lock = threading.Lock()
_next_fcallid = 1000
_exiting = False

# Special prefix for emitting function calls, e.g. "?mnsc:123 my_func [4, 5, 6]"
_FUNCTION_PREFIX = "?mnsc:"

class _ExecutorStack:
  """Stack of identifiers for function executors.

  The last/top ID determines which executor to use for servicing a script function.

  The executor IDs are:
    "T" -> execute on the tick loop
    "R" -> execute on the render loop
    "S" -> execute on the script loop
  """
  def __init__(self):
    self.thread_local = threading.local()

  def stack(self):
    # `None` is the sentinel value signaling an empty stack and default behavior.
    return self.thread_local.__dict__.setdefault("stack", [None])

  def push(self, executor_id: str):
    self.stack().append(executor_id)

  def pop(self):
    return self.stack().pop()

  def peek(self):
    return self.stack()[-1]

_executor_stack = _ExecutorStack()


class FunctionExecutor:
  def __init__(self, executor_id: str):
    self.executor_id = executor_id

  def __enter__(self):
    _executor_stack.push(self.executor_id)
    return self

  def __exit__(self, exc_type, exc_val, exc_tb):
    _executor_stack.pop()

# See common/src/main/java/net/minescript/common/FunctionExecutor.java
tick_loop = FunctionExecutor("T")
render_loop = FunctionExecutor("R")
script_loop = FunctionExecutor("S")

_default_executor = render_loop


def get_next_fcallid():
  global _next_fcallid
  with _script_function_calls_lock:
    _next_fcallid += 1
    return _next_fcallid

def call_noreturn_function(
    command: str, args,
    required_executor: FunctionExecutor = None, func_default_executor: FunctionExecutor = None):
  """Calls a function which does not return.

  Make a fire-and-forget function call with a call id of 0 and no return value.
  """
  if _exiting:
    debug_log("minescript_runtime.py: Ignoring script function while exiting:", command, args)
    return

  executor_id = (
      (required_executor and required_executor.executor_id) or
      _executor_stack.peek() or
      (func_default_executor and func_default_executor.executor_id) or
      _default_executor.executor_id)

  json_args = json.dumps(args)  # Do this outside the lock in case json.dumps has internal locking.
  with _script_function_calls_lock:
    print(f"{_FUNCTION_PREFIX}0 {executor_id} {command} {json_args}")


def send_script_function_request(func_name: str, args: Tuple[Any, ...],
                                 retval_handler: AnyConsumer,
                                 exception_handler: ExceptionHandler = None,
                                 required_executor: FunctionExecutor = None,
                                 func_default_executor: FunctionExecutor = None) -> int:
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

  if _exiting:
    debug_log("minescript_runtime.py: Ignoring script function while exiting:", func_name, args)
    return 0

  executor_id = (
      (required_executor and required_executor.executor_id) or
      _executor_stack.peek() or
      (func_default_executor and func_default_executor.executor_id) or
      _default_executor.executor_id)

  # Serialize JSON outside the lock in case json.dumps has internal locking that could deadlock.
  json_args = json.dumps(args)

  with _script_function_calls_lock:
    _next_fcallid += 1
    func_call_id = _next_fcallid
    _script_function_calls[func_call_id] = (func_name, retval_handler, exception_handler)
    print(f"{_FUNCTION_PREFIX}{func_call_id} {executor_id} {func_name} {json_args}")
  return func_call_id


_identity_fn = lambda x: x

_always_none_fn = lambda x: None

class FutureValue:
  def __init__(self, result_transform: Callable[[Any], Any] = _identity_fn, timeout_handler=None,
      cancel_handler=None):
    self.value_holder = []
    self.exception_holder = []
    self.result_transform = result_transform
    self.timeout_handler = timeout_handler
    self.cancel_handler = cancel_handler
    self.lock = threading.Lock()
    self.lock.acquire()

  def _set_value(self, value):
    self.value_holder.append(value)
    self.lock.release()

  def _raise_exception(self, e):
    self.exception_holder.append(e)
    self.lock.release()

  def cancel(self):
    if self.cancel_handler:
      self.cancel_handler()

  def wait(self, timeout=None):
    # Waiting while exiting would hang the process. So bail instead.
    if _exiting:
      return

    if not self.lock.acquire(timeout=(-1 if timeout is None else timeout)):
      if self.timeout_handler:
        self.timeout_handler()
      else:
        raise TimeoutError(f'Timeout after {timeout} seconds')

    self.lock.release()
    if self.exception_holder:
      raise self.exception_holder[0]
    elif self.value_holder:
      result = self.value_holder[0]
      return self.result_transform(result)
    else:
      raise ValueError("FutureValue has neither a value nor an exception")


def call_async_script_function(
    func_name: str,
    args: Tuple[Any, ...],
    required_executor: FunctionExecutor = None, func_default_executor: FunctionExecutor = None,
    result_transform: Callable[[Any], Any] = _identity_fn) -> FutureValue:
  """Calls a script function and awaits the function's return value.

  Args:
    func_name: name of Minescript function to call
    result_transform: if given, transform the return value

  Returns:
    script function's return value: number, string, list, or dict
  """
  future_value = FutureValue(result_transform=result_transform)

  func_call_id = send_script_function_request(
      func_name, args,
      future_value._set_value, future_value._raise_exception,
      required_executor, func_default_executor)

  def cancel_handler():
    # Special pseudo-function for cancelling the function call.
    cancel_args = (func_call_id, func_name)
    call_noreturn_function("cancelfn!", cancel_args)

  future_value.cancel_handler = cancel_handler
  return future_value


def await_script_function(
    func_name: str, args: Tuple[Any, ...],
    required_executor: FunctionExecutor = None, func_default_executor: FunctionExecutor = None):
  """Calls a script function and returns the function's return value.

  Args:
    func_name: name of Minescript function to call

  Returns:
    script function's return value: number, string, list, dict, or None
  """
  future_value = FutureValue()

  func_call_id = send_script_function_request(
      func_name, args,
      future_value._set_value, future_value._raise_exception,
      required_executor, func_default_executor)

  return future_value.wait()


@dataclass
class BasicTask:
  fcallid: int
  func_name: str
  immediate_args: Tuple[Any, ...]
  deferred_args: Tuple[Any, ...]
  result_transform: Callable[[Any], Any] = _identity_fn

  @staticmethod
  def _get_next_fcallid():
    return get_next_fcallid()

  @staticmethod
  def _get_immediate_args(args):
    return [None if isinstance(arg, BasicTask) else arg for arg in args]

  @staticmethod
  def _get_deferred_args(args):
    return [arg.fcallid if isinstance(arg, BasicTask) else None for arg in args]


class BasicScriptFunction:
  def __init__(self, name, args_func, conditional_task_arg=False):
    self.name = name
    self.args_func = args_func
    self.required_executor = None
    self.default_executor = None
    self.conditional_task_arg = conditional_task_arg
    self.result_transform = _always_none_fn

  def as_task(self, *args, **kwargs):
    fcallid = get_next_fcallid()
    if self.conditional_task_arg:
      kwargs["_as_task"] = True
    args_list = self.args_func(*args, **kwargs)
    immediate_args = BasicTask._get_immediate_args(args_list)
    deferred_args = BasicTask._get_deferred_args(args_list)
    return BasicTask(
        fcallid, self.name, immediate_args, deferred_args, self.result_transform)

  def set_default_executor(self, executor: FunctionExecutor):
    self.default_executor = executor

  def set_required_executor(self, executor: FunctionExecutor):
    self.required_executor = executor


class ScriptFunction(BasicScriptFunction):
  def __init__(self, name, args_func, result_transform=_identity_fn):
    super().__init__(name, args_func)
    self.result_transform = result_transform

  def __call__(self, *args, **kwargs):
    result = await_script_function(
        self.name, self.args_func(*args, **kwargs), self.required_executor, self.default_executor)
    return self.result_transform(result)

  def as_async(self, *args, **kwargs):
    return call_async_script_function(
        self.name, self.args_func(*args, **kwargs),
        self.required_executor, self.default_executor,
        self.result_transform)


class NoReturnScriptFunction(BasicScriptFunction):
  def __init__(self, name, args_func, conditional_task_arg=False):
    super().__init__(name, args_func, conditional_task_arg=conditional_task_arg)

  def __call__(self, *args, **kwargs):
    call_noreturn_function(
        self.name, self.args_func(*args, **kwargs), self.required_executor, self.default_executor)


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


def _ScriptServiceLoopImpl():
  while True:
    try:
      json_input = stdin_readline()
      if not json_input:
        debug_log("minescript_runtime.py: stdin reached EOF, exiting script service loop")
        break
      reply = json.loads(json_input)
      if _is_debug:
        debug_log("Parsed json reply:", reply)
    except json.decoder.JSONDecodeError as e:
      traceback.print_exc(file=sys.stderr)
      exception_message = f"JSON error in: {json_input}"
      debug_log(exception_message)
      print(exception_message, file=sys.stderr)
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
          debug_log("minescript_runtime.py: Got `exit!` response, exiting service loop")
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
        debug_log("minescript_runtime.py: Closing connection to fcall", func_call_id)
        del _script_function_calls[func_call_id]

    if "except" in reply:
      e = reply["except"]
      exception = JavaException(
          e["type"], e["message"], e["desc"],
          [StackElement(s["file"], s["method"], s["line"]) for s in e["stack"]])
      if exception_handler is None:
        exception_message = f"JavaException raised in `{func_name}`: {exception}"
        debug_log("minescript_runtime.py:", exception_message)
        print(exception_message, file=sys.stderr)
      else:
        if _is_debug:
          debug_log(
              f"minescript_runtime.py: Passing exception to handler for fcallid {func_call_id}:",
              exception)
        exception_handler(exception)
    elif "retval" in reply:
      retval = reply["retval"]
      if _is_debug: debug_log(f"Sending retval: {retval}")
      retval_handler(retval)

    if "conn" not in reply and "retval" not in reply and "except" not in reply:
      error_message = "minescript_runtime.py: " \
          f"Script function response missing 'conn', 'retval', and 'except': {reply}"
      debug_log(error_message)
      print(error_message, file=sys.stderr)


def _ScriptServiceLoop():
  global _exiting
  try:
    _ScriptServiceLoopImpl()
  finally:
    _exiting = True
    debug_log("minescript_runtime.py: Returning from _ScriptServiceLoop")


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
  debug_log("minescript_runtime.py: Main thread no longer alive, calling `exit!`")
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
