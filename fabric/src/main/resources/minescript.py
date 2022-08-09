# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

from minescript_runtime import CallScriptFunction, CallAsyncScriptFunction


def player_position(done_callback=None):
  """Gets the local player's position.

  Args:
    done_callback: if given, return immediately and call done_callback(return_value)
        asynchronously when return_value is ready

  Returns:
    if done_callback is None, returns player's position as [x, y, z]
  """
  if done_callback is None:
    return CallScriptFunction("player_position")
  else:
    CallAsyncScriptFunction("player_position", done_callback)


def getblock(x: int, y: int, z: int, done_callback=None):
  """Gets the type of block as position (x, y, z).

  Args:
    done_callback: if given, return immediately and call done_callback(return_value)
        asynchronously when return_value is ready

  Returns:
    if done_callback is None, returns the block type at (x, y, z) as a string
  """
  if done_callback is None:
    return CallScriptFunction("getblock", x, y, z)
  else:
    CallAsyncScriptFunction("getblock", (x, y, z), done_callback)


def await_loaded_region(x1: int, z1: int, x2: int, z2: int, done_callback=None):
  """Notifies the caller when the region from (x1, z1) to (x2, z2) is loaded.

  Args:
    done_callback: if given, return immediately and call done_callback(return_value)
        asynchronously when return_value is ready

  Returns:
    if done_callback is None, returns True when the requested region is fully loaded.
  """
  if done_callback is None:
    return CallScriptFunction("await_loaded_region", x1, z1, x2, z2)
  else:
    CallAsyncScriptFunction(
        "await_loaded_region", (x1, z1, x2, z2), done_callback)
