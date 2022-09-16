# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""minescript v2.0.1 distributed via Minescript jar file

Usage: import minescript  # from Python script

User-friendly API for scripts to make function calls into the
Minescript mod.  This module should be imported by other
scripts and not run directly.
"""

import os
import sys
from minescript_runtime import CallScriptFunction, CallAsyncScriptFunction
from typing import Any, List, Set, Dict, Tuple, Optional, Callable


def exec(command: str):
  """Executes the given command.

  If command doesn't already start with a slash or backslash, automatically
  prepends a slash. Ignores leading and trailing whitespace, and ignores empty
  commands.
  """
  command = command.strip()
  if not command:
    return
  if command[0] not in ("/", "\\"):
    command = "/" + command
  print(command)


def echo(message: Any):
  """Echoes message to the chat.

  The echoed message is visible only to the local player.
  """
  print(message, file=sys.stderr)


def chat(message: str):
  """Sends the given message to the chat.

  If message starts with a slash or backslash, automatically prepends a space
  so that the message is sent as a chat and not executed as a command.  Ignores
  empty messages.
  """
  if not message:
    return
  # If the message starts with a slash or backslash, prepend a space so that
  # the message is printed and not executed as a command.
  if message[0] in ("/", "\\"):
    message = " " + message
  print(message)


def screenshot(filename=None):
  """Takes a screenshot, similar to pressing the F2 key.

  Args:
    filename: if specified, screenshot filename relative to the screenshots directory; ".png"
      extension is added to the screenshot file if it doesn't already have a png extension.

  Returns:
    True is successful
  """
  if filename is None:
    return CallScriptFunction("screenshot")
  else:
    if os.path.sep in filename:
      echo(f'Error: `screenshot` does not support filenames with "{os.path.sep}" character.')
      return False
    else:
      if not filename.lower().endswith(".png"):
        filename += ".png"
      return CallScriptFunction("screenshot", filename)


def flush():
  """Wait for all previously issued script commands from this job to complete."""
  return CallScriptFunction("flush")


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


def player_go_forward(go_forward: bool):
  """Starts/stops moving the local player forward, similar to pressing/releasing the 'w' key.

  Args:
    go_forward: if True, go forward, otherwise stop going forward
  """
  return CallScriptFunction("player_go_forward", go_forward)


def player_orientation():
  """Gets the local player's orientation.

  Returns:
    (yaw: float, pitch: float) as angles in degrees
  """
  return CallScriptFunction("player_orientation")


def player_set_orientation(yaw: float, pitch: float):
  """Sets the local player's orientation.

  Args:
    yaw: degrees rotation of the local player's orientation around the y axis
    pitch: degrees rotation of the local player's orientation from the x-z plane

  Returns:
    True if successful
  """
  return CallScriptFunction("player_set_orientation", yaw, pitch)


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


def register_chat_message_listener(listener: Callable[[str], None]):
  """Registers a listener for receiving chat messages. One listener allowed per job.

  Args:
    listener: callable that repeatedly accepts a string representing chat messages
  """
  CallAsyncScriptFunction(
      "register_chat_message_listener", (), listener)


def unregister_chat_message_listener():
  """Unegisters a chat message listener, if any, for the currently running job.

  Returns:
    True if successfully unregistered a listener, False otherwise.
  """
  CallScriptFunction("unregister_chat_message_listener")
