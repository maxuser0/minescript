"""Interface for scripts to make function calls into the Minescript runtime.

CallScriptFunction(func_name):
  Makes a function call into the Minescript runtime, blocking execution until
  returning a value. The return value may be a string, numeric, or composite
  data such as a JSON array or structure.

CallAsyncScriptFunction(func_name, retval_handler):
  Makes a function call into the Minescript runtime, returning a value or
  stream of values asynchronously by invoking retval_handler(value),
  potentially multiple times. The number of times that the Minescript runtime
  calls retval_handler(value) is specific to each function.
"""

import json
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
      #print(f"json_input: {json_input}", file=sys.stderr)
      reply = json.loads(json_input)
    except json.decoder.JSONDecodeError as e:
      traceback.print_exc(file=sys.stderr)
      continue

    if "fcid" not in reply:
      print(
          "minescriptapi.py: 'fcid' field missing in script function response",
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
          f"minescriptapi.py: fcid={func_call_id} not found in _script_function_calls",
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
          f"minescriptapi.py: script function response has neither 'conn' nor 'retval': {reply}",
          file=sys.stderr)

  print(f"minescriptapi.py: Script finished: `{sys.argv[0]}`", file=sys.stderr);


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
