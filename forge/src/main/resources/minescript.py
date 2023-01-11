# SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""minescript v3.1 distributed via Minescript jar file

Usage: import minescript  # from Python script

User-friendly API for scripts to make function calls into the
Minescript mod.  This module should be imported by other
scripts and not run directly.
"""

import base64
import os
import sys
from array import array
from minescript_runtime import CallScriptFunction, CallAsyncScriptFunction
from typing import Any, List, Set, Dict, Tuple, Optional, Callable


def execute(command: str):
  """Executes the given command.

  If `command` doesn't already start with a slash or backslash, automatically
  prepends a slash. Ignores leading and trailing whitespace, and ignores empty
  commands.

  *Note: This was named `exec` in Minescript 2.0. The old name is no longer
  available in v3.0.*

  Since: v2.1
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

  Since: v2.0
  """
  print(message, file=sys.stderr)


def chat(message: str):
  """Sends the given message to the chat.

  If `message` starts with a slash or backslash, automatically prepends a space
  so that the message is sent as a chat and not executed as a command.  Ignores
  empty messages.

  Since: v2.0
  """
  if not message:
    return
  # If the message starts with a slash or backslash, prepend a space so that
  # the message is printed and not executed as a command.
  if message[0] in ("/", "\\"):
    message = " " + message
  print(message)


def log(message: str) -> bool:
  """Sends the given message to latest.log.

  Args:
    message: string to send to the log

  Returns:
    `True` if `message` was logged successfully.

  Since: v3.0
  """
  if not message:
    return
  CallScriptFunction("log", message)


def screenshot(filename=None) -> bool:
  """Takes a screenshot, similar to pressing the F2 key.

  Args:
    filename: if specified, screenshot filename relative to the screenshots directory; ".png"
      extension is added to the screenshot file if it doesn't already have a png extension.

  Returns:
    `True` is successful

  Since: v2.1
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
  """Wait for all previously issued script commands from this job to complete.

  Since: v2.1
  """
  return CallScriptFunction("flush")


def player_name() -> str:
  """Gets the local player's name.

  Since: v2.1
  """
  return CallScriptFunction("player_name")


def player_position(done_callback=None) -> List[float]:
  """Gets the local player's position.

  Args:
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    if `done_callback` is `None`, returns player's position as [x: float, y: float, z: float]
  """
  if done_callback is None:
    return CallScriptFunction("player_position")
  else:
    CallAsyncScriptFunction("player_position", done_callback)


def player_set_position(
    x: float, y: float, z: float, yaw: float = None, pitch: float = None) -> bool:
  """Sets the player's position, and optionally orientation.

  Note that in survival mode the server may reject the new coordinates if they're too far
  or require moving through walls.

  Args:
    x, y, z: position to try to move player to
    yaw, pitch: if not None, player's new orientation
  """
  return CallScriptFunction("player_set_position", x, y, z, yaw, pitch)


def player_hand_items(done_callback=None) -> List[Dict[str, Any]]:
  """Gets the items in the local player's hands.

  Args:
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    If `done_callback` is `None`, returns items in player's hands as a list of
    items where each item is a dict: `{"item": str, "count": int}`, plus
    `"nbt": str` if the item has NBT data; main-hand item is at list index 0,
    off-hand item at index 1.

  Since: v2.0
  """
  if done_callback is None:
    return CallScriptFunction("player_hand_items")
  else:
    CallAsyncScriptFunction("player_hand_items", done_callback)


def player_inventory(done_callback=None) -> List[Dict[str, Any]]:
  """Gets the items in the local player's inventory.

  Args:
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    If `done_callback` is `None`, returns items in player's inventory as list
    of items where each item is a dict: `{"item": str, "count": int, "slot":
    int}`, plus `"nbt": str` if an item has NBT data and `"selected": True` for
    the item selected in the player's main hand.

  Update in v3.0:
    Introduced `"slot"` and `"selected"` attributes in the returned
    dict, and `"nbt"` is populated only when NBT data is present. (In prior
    versions, `"nbt"` was always populated, with a value of `null` when NBT data
    was absent.)

  Since: v2.0
  """
  if done_callback is None:
    return CallScriptFunction("player_inventory")
  else:
    CallAsyncScriptFunction("player_inventory", done_callback)


def player_inventory_slot_to_hotbar(slot: int, done_callback=None) -> int:
  """Swaps an inventory item into the hotbar.

  Args:
    slot: inventory slot (9 or higher) to swap into the hotbar
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    If `done_callback` is `None`, returns the hotbar slot (0-8) that the inventory
    item was swapped into

  Since: v3.0
  """
  if done_callback is None:
    return CallScriptFunction("player_inventory_slot_to_hotbar", slot)
  else:
    CallAsyncScriptFunction("player_inventory_slot_to_hotbar", (slot,), done_callback)


def player_inventory_select_slot(slot: int, done_callback=None) -> int:
  """Selects the given slot within the player's hotbar.

  Args:
    slot: hotbar slot (0-8) to select in the player's hand
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    If `done_callback` is `None`, returns the previously selected hotbar slot

  Since: v3.0
  """
  if done_callback is None:
    return CallScriptFunction("player_inventory_select_slot", slot)
  else:
    CallAsyncScriptFunction("player_inventory_select_slot", (slot,), done_callback)


def player_press_forward(pressed: bool):
  """Starts/stops moving the local player forward, simulating press/release of the 'w' key.

  Args:
    pressed: if `True`, go forward, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_forward", pressed)


def player_press_backward(pressed: bool):
  """Starts/stops moving the local player backward, simulating press/release of the 's' key.

  Args:
    pressed: if `True`, go backward, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_backward", pressed)


def player_press_left(pressed: bool):
  """Starts/stops moving the local player to the left, simulating press/release of the 'a' key.

  Args:
    pressed: if `True`, move to the left, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_left", pressed)


def player_press_right(pressed: bool):
  """Starts/stops moving the local player to the right, simulating press/release of the 'd' key.

  Args:
    pressed: if `True`, move to the right, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_right", pressed)


def player_press_jump(pressed: bool):
  """Starts/stops the local player jumping, simulating press/release of the space key.

  Args:
    pressed: if `True`, jump, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_jump", pressed)


def player_press_sprint(pressed: bool):
  """Starts/stops the local player sprinting, simulating press/release of the left control key.

  Args:
    pressed: if `True`, sprint, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_sprint", pressed)


def player_press_sneak(pressed: bool):
  """Starts/stops the local player sneaking, simulating press/release of the left shift key.

  Args:
    pressed: if `True`, sneak, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_sneak", pressed)


def player_press_pick_item(pressed: bool):
  """Starts/stops the local player picking an item, simulating press/release of the middle mouse button.

  Args:
    pressed: if `True`, pick an item, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_pick_item", pressed)


def player_press_use(pressed: bool):
  """Starts/stops the local player using an item or selecting a block, simulating press/release of the right mouse button.

  Args:
    pressed: if `True`, use an item, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_use", pressed)


def player_press_attack(pressed: bool):
  """Starts/stops the local player attacking or breaking a block, simulating press/release of the left mouse button.

  Args:
    pressed: if `True`, press attack, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_attack", pressed)


def player_press_swap_hands(pressed: bool):
  """Starts/stops moving the local player swapping hands, simulating press/release of the 'f' key.

  Args:
    pressed: if `True`, swap hands, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_swap_hands", pressed)


def player_press_drop(pressed: bool):
  """Starts/stops the local player dropping an item, simulating press/release of the 'q' key.

  Args:
    pressed: if `True`, drop an item, otherwise stop doing so

  Since: v2.1
  """
  return CallScriptFunction("player_press_drop", pressed)


def player_orientation():
  """Gets the local player's orientation.

  Returns:
    (yaw: float, pitch: float) as angles in degrees

  Since: v2.1
  """
  return CallScriptFunction("player_orientation")


def player_set_orientation(yaw: float, pitch: float):
  """Sets the local player's orientation.

  Args:
    yaw: degrees rotation of the local player's orientation around the y axis
    pitch: degrees rotation of the local player's orientation from the x-z plane

  Returns:
    True if successful

  Since: v2.1
  """
  return CallScriptFunction("player_set_orientation", yaw, pitch)


def player_get_targeted_block(max_distance: float = 20):
  """Gets info about the nearest block, if any, in the local player's crosshairs.

  Args:
    max_distance: max distance from local player to look for blocks

  Returns:
    [[x, y, z], distance, side, block_description] if the local player has a
    block in their crosshairs within `max_distance`, `None` otherwise.
    `distance` (float) is calculated from the player to the targeted block;
    `side` (str) is the direction that the targeted side of the block is facing
    (e.g. `"east"`); `block_description` (str) describes the targeted block.

  Since: v3.0
  """
  return CallScriptFunction("player_get_targeted_block", max_distance)


def players():
  """Gets a list of nearby players and their attributes.

  Returns:
    List of players where each player is represented as a dict:
    `{"name": str, "type": str, "position": [float, float, float], "yaw": float, "pitch": float,
    "velocity": [float, float, float]}`

  Since: v2.1
  """
  return CallScriptFunction("players")


def entities():
  """Gets a list of nearby entities and their attributes.

  Returns:
    List of entities where each entity is represented as a dict:
    `{"name": str, "type": str, "position": [float, float, float], "yaw": float, "pitch": float,
    "velocity": [float, float, float]}`

  Since: v2.1
  """
  return CallScriptFunction("entities")


def getblock(x: int, y: int, z: int, done_callback=None):
  """Gets the type of block at position (x, y, z).

  Args:
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    if `done_callback` is `None`, returns the block type at (x, y, z) as a string
  """
  if done_callback is None:
    return CallScriptFunction("getblock", x, y, z)
  else:
    CallAsyncScriptFunction("getblock", (x, y, z), done_callback)


def getblocklist(positions: List[List[int]], done_callback=None):
  """Gets the types of block at the specified [x, y, z] positions.

  Args:
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    if `done_callback` is `None`, returns the block types at given positions as list of strings

  Since: v2.1
  """
  if done_callback is None:
    return CallScriptFunction("getblocklist", positions)
  else:
    CallAsyncScriptFunction("getblocklist", (positions,), done_callback)


def await_loaded_region(x1: int, z1: int, x2: int, z2: int, done_callback=None):
  """Notifies the caller when the region from (x1, z1) to (x2, z2) is loaded.

  Args:
    done_callback: if given, return immediately and call `done_callback(return_value)`
        asynchronously when `return_value` is ready

  Returns:
    if `done_callback` is `None`, returns `True` when the requested region is fully loaded.

  Examples:
    [1] Don't do any work until the region is done loading (synchronous / blocking
    call):

    ```
    minescript.echo("About to wait for region to load...")

    # Load all chunks within (x, z) bounds (0, 0) and (320, 160):
    minescript.await_loaded_region(0, 0, 320, 160)

    minescript.echo("Region finished loading.")
    ```

    [2] Continue doing work on the main thread while the region loads in the
    background (asynchronous / non-blocking call):

    ```
    import minescript
    import threading

    lock = threading.Lock()

    def on_region_loaded(loaded):
      if loaded:
        minescript.echo("Region loaded ok.")
      else:
        minescript.echo("Region failed to load.")
      lock.release()

    # Acquire the lock, to be released later by on_region_loaded().
    lock.acquire()

    # Calls on_region_loaded(...) when region finishes
    # loading all chunks within (x, z) bounds (0, 0)
    # and (320, 160):
    minescript.await_loaded_region(
        0, 0, 320, 160, on_region_loaded)

    minescript.echo("Do other work while region loads...")

    minescript.echo("Now wait for region to finish loading...")
    lock.acquire()

    minescript.echo("Do more work now that region finished loading...")
    ```
  """
  if done_callback is None:
    return CallScriptFunction("await_loaded_region", x1, z1, x2, z2)
  else:
    CallAsyncScriptFunction(
        "await_loaded_region", (x1, z1, x2, z2), done_callback)


def register_chat_message_listener(listener: Callable[[str], None]):
  """Registers a listener for receiving chat messages. One listener allowed per job.

  Listener receives both incoming and outgoing chat messages.

  Args:
    listener: callable that repeatedly accepts a string representing chat messages

  Since: v2.0

  See also:
    `register_chat_message_interceptor()` for swallowing outgoing chat messages
  """
  CallAsyncScriptFunction(
      "register_chat_message_listener", (), listener)


def unregister_chat_message_listener():
  """Unegisters a chat message listener, if any, for the currently running job.

  Returns:
    `True` if successfully unregistered a listener.

  Since: v2.0
  """
  CallScriptFunction("unregister_chat_message_listener")


def register_chat_message_interceptor(interceptor: Callable[[str], None]):
  """Registers an interceptor for swallowing chat messages.

  An interceptor swallows outgoing chat messages, typically for use in
  rewriting outgoing chat messages by calling minecraft.chat(str), e.g. to
  decorate or post-process outgoing messages automatically before they're sent
  to the server.  Only one interceptor is allowed at a time within a Minecraft
  instance.

  Args:
    interceptor: callable that repeatedly accepts a string representing chat messages

  Since: v2.1

  See also:
    `register_chat_message_listener()` for non-destructive listening of chat messages
  """
  CallAsyncScriptFunction(
      "register_chat_message_interceptor", (), interceptor)


def unregister_chat_message_interceptor():
  """Unegisters the chat message interceptor, if one is currently registered.

  Returns:
    `True` if successfully unregistered an interceptor.

  Since: v2.1
  """
  CallScriptFunction("unregister_chat_message_interceptor")


BlockPos = Tuple[int, int, int]
"""Tuple representing `(x: int, y: int, z: int)` position in block space."""


Rotation = Tuple[int, int, int, int, int, int, int, int, int]
"""Tuple of 9 `int` values representing a flattened, row-major 3x3 rotation matrix."""


class Rotations:
  """Common rotations for use with `BlockPack` and `BlockPacker` methods.

  Since: v3.0
  """

  IDENTITY: Rotation = (1, 0, 0, 0, 1, 0, 0, 0, 1)
  """Effectively no rotation."""

  X_90: Rotation = (1, 0, 0, 0, 0, 1, 0, -1, 0)
  """Rotate 90 degrees about the x axis."""

  X_180: Rotation = (1, 0, 0, 0, -1, 0, 0, 0, -1)
  """Rotate 180 degrees about the x axis."""

  X_270: Rotation = (1, 0, 0, 0, 0, -1, 0, 1, 0)
  """Rotate 270 degrees about the x axis."""

  Y_90: Rotation = (0, 0, 1, 0, 1, 0, -1, 0, 0)
  """Rotate 90 degrees about the y axis."""

  Y_180: Rotation = (-1, 0, 0, 0, 1, 0, 0, 0, -1)
  """Rotate 180 degrees about the y axis."""

  Y_270: Rotation = (0, 0, -1, 0, 1, 0, 1, 0, 0)
  """Rotate 270 degrees about the y axis."""

  Z_90: Rotation = (0, 1, 0, -1, 0, 0, 0, 0, 1)
  """Rotate 90 degrees about the z axis."""

  Z_180: Rotation = (-1, 0, 0, 0, -1, 0, 0, 0, 1)
  """Rotate 180 degrees about the z axis."""

  Z_270: Rotation = (0, -1, 0, 1, 0, 0, 0, 0, 1)
  """Rotate 270 degrees about the z axis."""

  INVERT_X: Rotation = (-1, 0, 0, 0, 1, 0, 0, 0, 1)
  """Invert the x coordinate (multiply by -1)."""

  INVERT_Y: Rotation = (1, 0, 0, 0, -1, 0, 0, 0, 1)
  """Invert the y coordinate (multiply by -1)."""

  INVERT_Z: Rotation = (1, 0, 0, 0, 1, 0, 0, 0, -1)
  """Invert the z coordinate (multiply by -1)."""


# TODO(maxuser): Move this into Rotations class and rename to compose(...).
def combine_rotations(rot1: Rotation, rot2: Rotation, /) -> Rotation:
  """Combines two rotation matrices into a single rotation matrix.

  Since: v3.0
  """
  return (
      rot1[0] * rot2[0] + rot1[1] * rot2[3] + rot1[2] * rot2[6],
      rot1[0] * rot2[1] + rot1[1] * rot2[4] + rot1[2] * rot2[7],
      rot1[0] * rot2[2] + rot1[1] * rot2[5] + rot1[2] * rot2[8],
      rot1[3] * rot2[0] + rot1[4] * rot2[3] + rot1[5] * rot2[6],
      rot1[3] * rot2[1] + rot1[4] * rot2[4] + rot1[5] * rot2[7],
      rot1[3] * rot2[2] + rot1[4] * rot2[5] + rot1[5] * rot2[8],
      rot1[6] * rot2[0] + rot1[7] * rot2[3] + rot1[8] * rot2[6],
      rot1[6] * rot2[1] + rot1[7] * rot2[4] + rot1[8] * rot2[7],
      rot1[6] * rot2[2] + rot1[7] * rot2[5] + rot1[8] * rot2[8])


def blockpack_read_world(
    pos1: BlockPos, pos2: BlockPos,
    rotation: Rotation = None, offset: BlockPos = None,
    comments: Dict[str, str] = {}, safety_limit: bool = True) -> int:
  """Creates a blockpack from blocks in the world within a rectangular volume.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    pos1, pos2: opposing corners of a rectangular volume from which to read world blocks
    rotation: rotation matrix to apply to block coordinates read from world
    offset: offset to apply to block coordiantes (applied after rotation)
    comments: key, value pairs to include in the new blockpack
    safety_limit: if `True`, fail if requested volume spans more than 1600 chunks

  Returns:
    an int id associated with a new blockpack upon success, `None` otherwise

  Since: v3.0
  """
  return CallScriptFunction(
      "blockpack_read_world", pos1, pos2, rotation, offset, comments, safety_limit)


def blockpack_read_file(filename: str) -> int:
  """Reads a blockpack from a file.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    filename: name of file to read; relative to Minecraft dir unless it's an absolute path
        (".zip" is automatically appended to filename if it does not end with that extension)

  Returns:
    an int id associated with a blockpack upon success, `None` otherwise

  Since: v3.0
  """
  return CallScriptFunction("blockpack_read_file", filename)


def blockpack_import_data(base64_data: str) -> int:
  """Creates a blockpack from base64-encoded serialized blockpack data.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    base64_data: base64-encoded string containing serialization of blockpack data.

  Returns:
    an int id associated with a blockpack upon success, `None` otherwise

  Since: v3.0
  """
  return CallScriptFunction("blockpack_import_data", base64_data)


def blockpack_block_bounds(blockpack_id: int) -> (BlockPos, BlockPos):
  """Returns bounding coordinates of blocks in the blockpack associated with blockpack_id.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Since: v3.0
  """
  return CallScriptFunction("blockpack_block_bounds", blockpack_id)


def blockpack_comments(blockpack_id: int) -> Dict[str, str]:
  """Returns comments stored in the blockpack associated with blockpack_id.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Since: v3.0
  """
  return CallScriptFunction("blockpack_comments", blockpack_id)


def blockpack_write_world(
    blockpack_id: int, rotation: Rotation = None, offset: BlockPos = None) -> bool:
  """Writes blocks from a blockpack into the current world. Requires setblock and fill commands.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    blockpack_id: id of a currently loaded blockpack
    rotation: rotation matrix to apply to block coordinates before writing to world
    offset: offset to apply to block coordiantes (applied after rotation)

  Returns:
    `True` upon success

  Since: v3.0
  """
  return CallScriptFunction("blockpack_write_world", blockpack_id, rotation, offset)


def blockpack_write_file(blockpack_id: int, filename: str) -> bool:
  """Writes a blockpack to a file.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    blockpack_id: id of a currently loaded blockpack
    filename: name of file to write; relative to Minecraft dir unless it's an absolute path
        (".zip" is automatically appended to filename if it does not end with that extension)

  Returns:
    `True` upon success

  Since: v3.0
  """
  return CallScriptFunction("blockpack_write_file", blockpack_id, filename)


def blockpack_export_data(blockpack_id: int) -> str:
  """Serializes a blockpack into a base64-encoded string.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    blockpack_id: id of a currently loaded blockpack

  Returns:
    a base64-encoded string containing a serialized blockpack

  Since: v3.0
  """
  return CallScriptFunction("blockpack_export_data", blockpack_id)


def blockpack_delete(blockpack_id: int) -> bool:
  """Frees a currently loaded blockpack to be garbage collected.

  For a more user-friendly API, use the `BlockPack` class instead. (__internal__)

  Args:
    blockpack_id: id of a currently loaded blockpack

  Returns:
    `True` upon success

  Since: v3.0
  """
  return CallScriptFunction("blockpack_delete", blockpack_id)


def blockpacker_create() -> int:
  """Creates a new, empty blockpacker.

  For a more user-friendly API, use the `BlockPacker` class instead. (__internal__)

  Returns:
    an int id associated with a new blockpacker

  Since: v3.0
  """
  return CallScriptFunction("blockpacker_create")


def blockpacker_add_blocks(
    blockpacker_id: int, offset: BlockPos,
    base64_setblocks: str, base64_fills: str, blocks: List[str]) -> bool:
  """Adds blocks from setblocks and fills arrays to a currently loaded blockpacker.

  For a more user-friendly API, use the `BlockPacker` class instead. (__internal__)

  Args:
    blockpacker_id: id of a currently loaded blockpacker
    offset: offset from 16-bit positions in `base64_setblocks` and `base64_fills`
    base64_setblocks: base64-encoded array of 16-bit signed ints where every 4 values are:
      x, y, z relative to `offset` and index into `blocks` list
    base64_fills: base64-encoded array of 16-bit signed ints where every 7 values are:
      x1, y1, z1, x2, y2, z2 relative to `offset` and index into `blocks` list
    blocks: types of blocks referenced from `base64_setblocks` and `base64_fills` arrays

  Returns:
    `True` upon success

  Since: v3.1
  """
  return CallScriptFunction(
      "blockpacker_add_blocks", blockpacker_id, offset, base64_setblocks, base64_fills, blocks)


def blockpacker_add_blockpack(
    blockpacker_id: int, blockpack_id: int,
    rotation: Rotation = None, offset: BlockPos = None) -> bool:
  """Adds the blocks within a currently loaded blockpack into a blockpacker.

  For a more user-friendly API, use the `BlockPacker` class instead. (__internal__)

  Args:
    blockpacker_id: id of a blockpacker to receive blocks
    blockpack_id: id of a blockpack from which to copy blocks
    rotation: rotation matrix to apply to block coordinates before adding to blockpacker
    offset: offset to apply to block coordiantes (applied after rotation)

  Returns:
    `True` upon success

  Since: v3.0
  """
  return CallScriptFunction(
      "blockpacker_add_blockpack", blockpacker_id, blockpack_id, rotation, offset)


def blockpacker_pack(blockpacker_id: int, comments: Dict[str, str]) -> int:
  """Packs blocks within a blockpacker into a new blockpack.

  For a more user-friendly API, use the `BlockPacker` class instead. (__internal__)

  Args:
    blockpacker_id: id of a currently loaded blockpacker
    comments: key, value pairs to include in the new blockpack

  Returns:
    int id for a new blockpack containing a snapshot of blocks from the blockpacker

  Since: v3.0
  """
  return CallScriptFunction("blockpacker_pack", blockpacker_id, comments)


def blockpacker_delete(blockpacker_id: int) -> bool:
  """Frees a currently loaded blockpacker to be garbage collected.

  For a more user-friendly API, use the `BlockPacker` class instead. (__internal__)

  Args:
    blockpacker_id: id of a currently loaded blockpacker

  Returns:
    `True` upon success

  Since: v3.0
  """
  return CallScriptFunction("blockpacker_delete", blockpacker_id)


class BlockPackException(Exception):
  pass


class BlockPack:
  """BlockPack is an immutable and serializable collection of blocks.

  A blockpack can be read from or written to worlds, files, and serialized
  bytes. Although blockpacks are immutable and preserve position and
  orientation of blocks, they can be rotated and offset when read from or
  written to worlds.

  For a mutable collection of blocks, see `BlockPacker`.

  Since: v3.0
  """

  def __init__(self, java_generated_id: int):
    """Do not call the constructor directly. Use factory classmethods instead.

    (__internal__)
    """
    self._id = java_generated_id

  @classmethod
  def read_world(
      cls,
      pos1: BlockPos, pos2: BlockPos, *,
      rotation: Rotation = None, offset: BlockPos = None,
      comments: Dict[str, str] = {}, safety_limit: bool = True) -> 'BlockPack':
    """Creates a blockpack from blocks in the world within a rectangular volume.

    Args:
      pos1, pos2: opposing corners of a rectangular volume from which to read world blocks
      rotation: rotation matrix to apply to block coordinates read from world
      offset: offset to apply to block coordiantes (applied after rotation)
      comments: key, value pairs to include in the new blockpack
      safety_limit: if `True`, fail if requested volume spans more than 1600 chunks

    Returns:
      a new BlockPack containing blocks read from the world

    Raises:
      `BlockPackException` if blockpack cannot be read
    """
    blockpack_id = blockpack_read_world(pos1, pos2, rotation, offset, comments, safety_limit)
    if blockpack_id is None:
      raise BlockPackException()
    return BlockPack(blockpack_id)


  @classmethod
  def read_file(cls, filename: str, *, relative_to_cwd=False) -> 'BlockPack':
    """Reads a blockpack from a file.

    Args:
      filename: name of file relative to minescript/blockpacks dir unless it's an absolute path
        (".zip" is automatically appended to filename if it does not end with that extension)
      relative_to_cwd: if `True`, relative filename is taken to be relative to Minecraft dir

    Returns:
      a new BlockPack containing blocks read from the file

    Raises:
      `BlockPackException` if blockpack cannot be read
    """
    if not os.path.isabs(filename) and not relative_to_cwd:
      filename = os.path.join("minescript", "blockpacks", filename)
    blockpack_id = blockpack_read_file(filename)
    if blockpack_id is None:
      raise BlockPackException()
    return BlockPack(blockpack_id)

  @classmethod
  def import_data(cls, base64_data: str) -> 'BlockPack':
    """Creates a blockpack from base64-encoded serialized blockpack data.

    Args:
      base64_data: base64-encoded string containing serialization of blockpack data.

    Returns:
      a new BlockPack containing blocks read from the base64-encoded data

    Raises:
      `BlockPackException` if blockpack cannot be read
    """
    blockpack_id = blockpack_import_data(base64_data)
    if blockpack_id is None:
      raise BlockPackException()
    return BlockPack(blockpack_id)

  def block_bounds(self) -> (BlockPos, BlockPos):
    """Returns min and max bounding coordinates of blocks in this BlockPack.

    Raises:
      `BlockPackException` if blockpack cannot be accessed
    """
    bounds = blockpack_block_bounds(self._id)
    if bounds is None:
      raise BlockPackException()
    return bounds


  def comments(self) -> Dict[str, str]:
    """Returns comments stored in this BlockPack.

    Raises:
      `BlockPackException` if blockpack cannot be accessed

    Raises:
      `BlockPackException` if blockpack operation fails
    """
    comments = blockpack_comments(self._id)
    if comments is None:
      raise BlockPackException()
    return comments


  def write_world(self, *, rotation: Rotation = None, offset: BlockPos = None):
    """Writes blocks from this BlockPack into the current world. Requires setblock, fill commands.

    Args:
      rotation: rotation matrix to apply to block coordinates before writing to world
      offset: offset to apply to block coordiantes (applied after rotation)

    Raises:
      `BlockPackException` if blockpack operation fails
    """
    if not blockpack_write_world(self._id, rotation, offset):
      raise BlockPackException()


  def write_file(self, filename: str, *, relative_to_cwd=False):
    """Writes this BlockPack to a file.

    Args:
      filename: name of file relative to minescript/blockpacks dir unless it's an absolute path
        (".zip" is automatically appended to filename if it does not end with that extension)
      relative_to_cwd: if `True`, relative filename is taken to be relative to Minecraft dir

    Raises:
      `BlockPackException` if blockpack operation fails
    """
    if not os.path.isabs(filename) and not relative_to_cwd:
      filename = os.path.join("minescript", "blockpacks", filename)
    if not blockpack_write_file(self._id, filename):
      raise BlockPackException()


  def export_data(self) -> str:
    """Serializes this BlockPack into a base64-encoded string.

    Returns:
      a base64-encoded string containing this blockpack's data

    Raises:
      `BlockPackException` if blockpack operation fails
    """
    base64_str = blockpack_export_data(self._id)
    if base64_str is None:
      raise BlockPackException()
    return base64_str


  def __del__(self):
    """Frees this BlockPack to be garbage collected.

    Raises:
      `BlockPackException` if blockpack operation fails
    """
    if not blockpack_delete(self._id):
      raise BlockPackException()


class BlockPackerException(Exception):
  pass


def _pos_subtract(pos1: BlockPos, pos2: BlockPos) -> BlockPos:
  """Returns pos1 minus pos2."""
  return (pos1[0] - pos2[0], pos1[1] - pos2[1], pos1[2] - pos2[2])


_SETBLOCKS_ARRAY_THRESHOLD = 4000
_FILLS_ARRAY_THRESHOLD = 7000
_BLOCKS_DICT_THRESHOLD = 1000

class BlockPacker:
  """BlockPacker is a mutable collection of blocks.

  Blocks can be added to a blockpacker by calling `setblock(...)`, `fill(...)`,
  and/or `add_blockpack(...)`.  To serialize blocks or write them to a world, a
  blockpacker can be "packed" by calling pack() to create a compact snapshot of
  the blocks contained in the blockpacker in the form of a new BlockPack. A
  blockpacker continues to store the same blocks it had before being packed,
  and more blocks can be added thereafter.

  For a collection of blocks that is immutable and serializable, see `BlockPack`.

  Since: v3.0
  """

  def __init__(self):
    """Creates a new, empty blockpacker."""

    self._id = blockpacker_create()
    self.offset = None # offset for 16-bit positions recorded in setblocks and fills
    self.setblocks = array("h")
    self.fills = array("h")
    self.blocks: Dict[str, int] = dict()

  def _get_block_id(self, block_type: str) -> int:
    return self.blocks.setdefault(block_type, len(self.blocks))

  def setblock(self, pos: BlockPos, block_type: str):
    """Sets a block within this BlockPacker.

    Args:
      pos: position of a block to set
      block_type: block descriptor to set

    Raises:
      `BlockPackerException` if blockpacker operation fails
    """
    if self.offset is None:
      self.offset = pos

    relative_pos = _pos_subtract(pos, self.offset)
    if max(relative_pos) > 32767 or min(relative_pos) < -32768:
      echo(
          f"Blocks within a Python-generated BlockPacker cannot span more than 32,767 blocks: "
          f"{self.offset} -> {pos}")
      raise BlockPackerException()

    self.setblocks.extend(relative_pos)
    self.setblocks.append(self._get_block_id(block_type))

    if (len(self.setblocks) > _SETBLOCKS_ARRAY_THRESHOLD or
        len(self.blocks) > _BLOCKS_DICT_THRESHOLD):
      self._flush_blocks()

  def fill(self, pos1: BlockPos, pos2: BlockPos, block_type: str):
    """Fills blocks within this BlockPacker.

    Args:
      pos1, pos2: coordinates of opposing corners of a rectangular volume to fill
      block_type: block descriptor to fill

    Raises:
      `BlockPackerException` if blockpacker operation fails
    """
    if self.offset is None:
      self.offset = pos1

    relative_pos1 = _pos_subtract(pos1, self.offset)
    if max(relative_pos1) > 32767 or min(relative_pos1) < -32768:
      echo(
          f"Blocks within a Python-generated BlockPacker cannot span more than 32,767 blocks: "
          f"{self.offset} -> {pos1}")
      raise BlockPackerException()

    relative_pos2 = _pos_subtract(pos2, self.offset)
    if max(relative_pos2) > 32767 or min(relative_pos2) < -32768:
      echo(
          f"Blocks within a Python-generated BlockPacker cannot span more than 32,767 blocks: "
          f"{self.offset} -> {pos2}")
      raise BlockPackerException()

    self.fills.extend(relative_pos1)
    self.fills.extend(relative_pos2)
    self.fills.append(self._get_block_id(block_type))

    if (len(self.fills) > _FILLS_ARRAY_THRESHOLD or
        len(self.blocks) > _BLOCKS_DICT_THRESHOLD):
      self._flush_blocks()

  def _flush_blocks(self):
    if sys.byteorder != "big":
      # Swap to network (big-endian) byte order.
      self.setblocks.byteswap()
      self.fills.byteswap()

    ok = blockpacker_add_blocks(
        self._id, self.offset,
        base64.b64encode(self.setblocks.tobytes()).decode("utf-8"),
        base64.b64encode(self.fills.tobytes()).decode("utf-8"),
        list(self.blocks.keys()))

    self.offset = None
    self.setblocks = array("h")
    self.fills = array("h")
    self.blocks = dict()

    if not ok:
      raise BlockPackerException()

  def add_blockpack(
      self, blockpack: BlockPack, *, rotation: Rotation = None, offset: BlockPos = None):
    """Adds the blocks within a BlockPack into this BlockPacker.

    Args:
      blockpack: BlockPack from which to copy blocks
      rotation: rotation matrix to apply to block coordinates before adding to blockpacker
      offset: offset to apply to block coordiantes (applied after rotation)

    Raises:
      `BlockPackerException` if blockpacker operation fails
    """
    if not blockpacker_add_blockpack(self._id, blockpack._id, rotation, offset):
      raise BlockPackerException()

  def pack(self, *, comments: Dict[str, str] = {}) -> BlockPack:
    """Packs blocks within this BlockPacker into a new BlockPack.

    Args:
      comments: key, value pairs to include in the new BlockPack

    Returns:
      a new BlockPack containing a snapshot of blocks from this BlockPacker

    Raises:
      `BlockPackerException` if blockpacker operation fails
    """
    self._flush_blocks()
    return BlockPack(blockpacker_pack(self._id, comments))

  def __del__(self):
    """Frees this BlockPacker to be garbage collected.

    Raises:
      `BlockPackerException` if blockpacker operation fails
    """
    if not blockpacker_delete(self._id):
      raise BlockPackerException()

