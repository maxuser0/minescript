# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""minescript v1.19.2 distributed via Minescript jar file

Usage: import minescript  # from Python script

User-friendly API for scripts to make function calls into the
Minescript mod.  This module should be imported by other
scripts and not run directly.
"""

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


def player_hand_items(done_callback=None):
  """Gets the items in the local player's hands.

  Args:
    done_callback: if given, return immediately and call done_callback(return_value)
        asynchronously when return_value is ready

  Returns:
    If done_callback is None, returns items in player's inventory as list of
    items where each item is a dict: {"item": str, "count": int, "nbt": str}
  """
  if done_callback is None:
    return CallScriptFunction("player_hand_items")
  else:
    CallAsyncScriptFunction("player_hand_items", done_callback)


def player_inventory(done_callback=None):
  """Gets the items in the local player's inventory.

  Args:
    done_callback: if given, return immediately and call done_callback(return_value)
        asynchronously when return_value is ready

  Returns:
    If done_callback is None, returns items in player's inventory as list of
    items where each item is a dict: {"item": str, "count": int, "nbt": str}
  """
  if done_callback is None:
    return CallScriptFunction("player_inventory")
  else:
    CallAsyncScriptFunction("player_inventory", done_callback)


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
