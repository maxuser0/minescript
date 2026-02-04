# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""minescript v5.0 distributed via Minescript jar file

Minescript standard library for Pyjinn scripts.
"""

import system.pyj.sys as sys

if "Pyjinn" not in sys.version:
  # This import is for giving IDEs access to the Python definitions of dataclasses that are
  # not needed by Pyjinn scripts because they access the Java objects directly.
  from system.lib.minescript import *
  from typing import List, Callable, Union
  __script__ = None

  def JavaClass(name: str):
    """Pyjinn built-in function."""
    raise NotImplementedError("JavaClass is implemented in Java for Pyjinn scripts")

  def JavaList(a_list: List[Any]):
    """Pyjinn built-in function."""
    raise NotImplementedError("JavaList is implemented in Java for Pyjinn scripts")

  def JavaArray(a_tuple: Tuple[Any, ...], clazz=None):
    """Pyjinn built-in function."""
    raise NotImplementedError("JavaArray is implemented in Java for Pyjinn scripts")

  def JavaString(string: str):
    """Pyjinn built-in function."""
    raise NotImplementedError("JavaString is implemented in Java for Pyjinn scripts")


_System = JavaClass("java.lang.System")
Minescript = JavaClass("net.minescript.common.Minescript")
BlockPack = JavaClass("net.minescript.common.pyjinn.BlockPack")
BlockPacker = JavaClass("net.minescript.common.pyjinn.BlockPacker")
Script = JavaClass("org.pyjinn.interpreter.Script")
_Coroutine = JavaClass("org.pyjinn.interpreter.Coroutine")
_RuntimeException = JavaClass("java.lang.RuntimeException")


def __mcall__(name: str, args):
  return Minescript.call(__script__.vars["job"], 0, name, JavaList(args))


def execute(command: str):
  """Executes the given command.

  If `command` is prefixed by a backslash, it's treated as Minescript command,
  otherwise it's treated as a Minecraft command (the slash prefix is optional).

  *Note: This was named `exec` in Minescript 2.0. The old name is no longer
  available in v3.0.*

  Since: v2.1
  """
  return __mcall__("execute", [command])


def echo(*messages):
  """Echoes plain-text messages to the chat.

  Echoed messages are visible only to the local player.

  If multiple args are given, join messages with a space separating them.

  Update in v4.0:
    Support multiple plain-text messages.

  Since: v2.0
  """
  return __mcall__("echo", [" ".join([str(m) for m in messages])])


def echo_json(json_text):
  """Echoes JSON-formatted text to the chat.

  Echoed text is visible only to the local player.

  TODO(maxuser): Support the following:
  `json_text` may be a string representing JSON text, or a list or dict. If it's a list or dict,
  convert it to a JSON string using the standard `json` module.

  Since: v4.0
  """
  return __mcall__("echo_json", [json_text])


def chat(*messages):
  """Sends messages to the chat.

  If `messages[0]` is a str starting with a slash or backslash, automatically
  prepends a space so that the messages are sent as a chat and not executed as
  a command. If `len(messages)` is greater than 1, join messages with a space
  separating them.  Ignores empty `messages`.

  Update in v4.0:
    Support multiple messages.

  Since: v2.0
  """
  return __mcall__("chat", [" ".join([str(m) for m in messages])])


def log(*messages):
  """Sends messages to latest.log.

  Update in v4.0:
    Support multiple messages of any type. Auto-convert messages to `str`.

  Since: v3.0
  """
  return __mcall__("log", [" ".join([str(m) for m in messages])])


def screenshot(filename: str=None):
  """Takes a screenshot, similar to pressing the F2 key.

  Args:
    filename: if specified, screenshot filename relative to the screenshots directory; ".png"
      extension is added to the screenshot file if it doesn't already have a png extension.

  Since: v2.1
  """
  return __mcall__("screenshot", [filename])


def job_info() -> List[JobInfo]:
  """Return info about active Minescript jobs.

  Returns:
    `JobInfo`.  For the  enclosing job, `JobInfo.self` is `True`.

  Since: v4.0
  """
  return __mcall__("job_info", [])


def player_name() -> str:
  """Gets the local player's name.

  Since: v2.1
  """
  return __mcall__("player_name", [])


def player_position() -> List[float]:
  """Gets the local player's position.

  Returns:
    player's position as [x: float, y: float, z: float]

  Update in v4.0:
    Removed `done_callback` arg. Use `player_position().as_async()` for async execution.
  """
  return __mcall__("player_position", [])


def player_hand_items() -> HandItems:
  """Gets the items in the local player's hands.

  Returns:
    Items in player's hands.
    (Legacy-style return value can be restored with `options.legacy_dict_return_values = True`)

  Update in v4.0:
    Return `HandItems` instead of `List[Dict[str, Any]]` by default.
    Removed `done_callback` arg. Use `player_hand_items.as_async()` for async execution.

  Since: v2.0
  """
  return __mcall__("player_hand_items", [])


def player_inventory() -> List[ItemStack]:
  """Gets the items in the local player's inventory.

  Returns:
    Items in player's inventory.
    (Legacy-style return value can be restored with `options.legacy_dict_return_values = True`)

  Update in v4.0:
    Return `List[ItemStack]` instead of `List[Dict[str, Any]]` by default.
    Removed `done_callback` arg. Use `player_inventory.as_async()` for async execution.

  Update in v3.0:
    Introduced `"slot"` and `"selected"` attributes in the returned
    dict, and `"nbt"` is populated only when NBT data is present. (In prior
    versions, `"nbt"` was always populated, with a value of `null` when NBT data
    was absent.)

  Since: v2.0
  """
  return __mcall__("player_inventory", [])


def player_inventory_select_slot(slot: int) -> int:
  """Selects the given slot within the player's hotbar.

  Args:
    slot: hotbar slot (0-8) to select in the player's hand

  Returns:
    previously selected hotbar slot

  Update in v4.0:
    Removed `done_callback` arg. Use `player_inventory_select_slot.as_async(...)` for async execution.

  Since: v3.0
  """
  return __mcall__("player_inventory_select_slot", [slot])


def press_key_bind(key_mapping_name: str, pressed: bool):
  """Presses/unpresses a mapped key binding.

  Valid values of `key_mapping_name` include: "key.advancements", "key.attack", "key.back",
  "key.chat", "key.command", "key.drop", "key.forward", "key.fullscreen", "key.hotbar.1",
  "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5", "key.hotbar.6", "key.hotbar.7",
  "key.hotbar.8", "key.hotbar.9", "key.inventory", "key.jump", "key.left",
  "key.loadToolbarActivator", "key.pickItem", "key.playerlist", "key.right",
  "key.saveToolbarActivator", "key.screenshot", "key.smoothCamera", "key.sneak",
  "key.socialInteractions", "key.spectatorOutlines", "key.sprint", "key.swapOffhand",
  "key.togglePerspective", "key.use"

  Args:
    key_mapping_name: name of key binding
    pressed: if `True`, press the bound key, otherwise unpress it

  Since: v4.0
  """
  return set_timeout(lambda: __mcall__("press_key_bind", [key_mapping_name, pressed]), 0)


def player_press_forward(pressed: bool):
  """Starts/stops moving the local player forward, simulating press/release of the 'w' key.

  Args:
    pressed: if `True`, go forward, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_forward", [pressed]), 0)


def player_press_backward(pressed: bool):
  """Starts/stops moving the local player backward, simulating press/release of the 's' key.

  Args:
    pressed: if `True`, go backward, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_backward", [pressed]), 0)


def player_press_left(pressed: bool):
  """Starts/stops moving the local player to the left, simulating press/release of the 'a' key.

  Args:
    pressed: if `True`, move to the left, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_left", [pressed]), 0)


def player_press_right(pressed: bool):
  """Starts/stops moving the local player to the right, simulating press/release of the 'd' key.

  Args:
    pressed: if `True`, move to the right, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_right", [pressed]), 0)


def player_press_jump(pressed: bool):
  """Starts/stops the local player jumping, simulating press/release of the space key.

  Args:
    pressed: if `True`, jump, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_jump", [pressed]), 0)


def player_press_sprint(pressed: bool):
  """Starts/stops the local player sprinting, simulating press/release of the left control key.

  Args:
    pressed: if `True`, sprint, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_sprint", [pressed]), 0)


def player_press_sneak(pressed: bool):
  """Starts/stops the local player sneaking, simulating press/release of the left shift key.

  Args:
    pressed: if `True`, sneak, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_sneak", [pressed]), 0)


def player_press_pick_item(pressed: bool):
  """Starts/stops the local player picking an item, simulating press/release of the middle mouse button.

  Args:
    pressed: if `True`, pick an item, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_pick_item", [pressed]), 0)


def player_press_use(pressed: bool):
  """Starts/stops the local player using an item or selecting a block, simulating press/release of the right mouse button.

  Args:
    pressed: if `True`, use an item, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_use", [pressed]), 0)


def player_press_attack(pressed: bool):
  """Starts/stops the local player attacking or breaking a block, simulating press/release of the left mouse button.

  Args:
    pressed: if `True`, press attack, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_attack", [pressed]), 0)


def player_press_swap_hands(pressed: bool):
  """Starts/stops moving the local player swapping hands, simulating press/release of the 'f' key.

  Args:
    pressed: if `True`, swap hands, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_swap_hands", [pressed]), 0)


def player_press_drop(pressed: bool):
  """Starts/stops the local player dropping an item, simulating press/release of the 'q' key.

  Args:
    pressed: if `True`, drop an item, otherwise stop doing so

  Since: v2.1
  """
  return set_timeout(lambda: __mcall__("player_press_drop", [pressed]), 0)


def player_orientation() -> List[float]:
  """Gets the local player's orientation.

  Returns:
    [yaw: float, pitch: float] as angles in degrees

  Since: v2.1
  """
  return __mcall__("player_orientation", [])


def player_set_orientation(yaw: float, pitch: float) -> bool:
  """Sets the local player's orientation.

  Args:
    yaw: degrees rotation of the local player's orientation around the y axis
    pitch: degrees rotation of the local player's orientation from the x-z plane

  Returns:
    True if successful

  Since: v2.1
  """
  return __mcall__("player_set_orientation", [yaw, pitch])


def player_get_targeted_block(max_distance: float = 20) -> Union[TargetedBlock, None]:
  """Gets info about the nearest block, if any, in the local player's crosshairs.

  Args:
    max_distance: max distance from local player to look for blocks

  Returns:
    `TargetedBlock` for the block targeted by the player, or `None` if no block is targeted.

  Update in v4.0:
    Return value changed from `list` to `TargetedBlock`.

  Since: v3.0
  """
  return __mcall__("player_get_targeted_block", [max_distance])


def player_get_targeted_entity(max_distance: float = 20, nbt: bool = False) -> Union[EntityData, None]:
  """Gets the entity targeted in the local player's crosshairs, if any.

  Args:
    max_distance: maximum distance to check for targeted entities
    nbt: if `True`, populate an `"nbt"` attribute for the player

  Returns:
    `EntityData` for the entity targeted by the player, or `None` if no entity is targeted.
    (Legacy-style returned dict can be restored with `options.legacy_dict_return_values = True`)

  Since: v4.0
  """
  return __mcall__("player_get_targeted_entity", [max_distance, nbt])


def player_health() -> float:
  """Gets the local player's health.

  Since: v3.1
  """
  return __mcall__("player_health", [])


def player(nbt: bool = False) -> EntityData:
  """Gets attributes for the local player.

  Args:
    nbt: if `True`, populate the `nbt` field for the player

  Returns:
    `EntityData` representing a snapshot of values for the local player.
    (Legacy-style returned dict can be restored with `options.legacy_dict_return_values = True`)

  Since: v4.0
  """
  return __mcall__("player", [nbt])

get_player = player  # Alias for scripts with `player` variables.


def players(
    nbt: bool = False, uuid: str = None, name: str = None,
    position: Vector3f = None, offset: Vector3f = None, min_distance: float = None,
    max_distance: float = None, sort: str = None, limit: int = None) -> List[EntityData]:
  """Gets a list of nearby players and their attributes.

  Args:
    nbt: if `True`, populate an `"nbt"` attribute for each returned player
    uuid: regular expression for matching entities' UUIDs (optional)
    name: regular expression for matching entities' names (optional)
    position: position used with `offset`, `min_distance`, or `max_distance` to define a
        volume for filtering entities; default is the local player's position (optional)
    offset: offset relative to `position` for selecting entities (optional)
    min_distance: min distance relative to `position` for selecting entities (optional)
    max_distance: max distance relative to `position` for selecting entities (optional)
    sort: one of "nearest", "furthest", "random", or "arbitrary" (optional)
    limit: maximum number of entities to return (optional)

  Returns:
    `List[EntityData]` representing a snapshot of values for the selected players.
    (Legacy returned dicts can be restored with `options.legacy_dict_return_values = True`)

  Update in v4.0:
    Added args: uuid, name, type, position, offset, min_distance, max_distance, sort, limit.
    Return `List[EntityData]` instead of `List[Dict[str, Any]]` by default.
    Added `uuid` and `id` to returned players.

  Update in v3.1:
    Added `"health"` and `"local"` attributes, and `nbt` arg to output `"nbt"`
    attribute.

  Since: v2.1
  """
  return __mcall__(
      "players",
      [nbt, uuid, name, position, offset, min_distance, max_distance, sort, limit])

get_players = players  # Alias for scripts with `players` variables.


def entities(
    nbt: bool = False, uuid: str = None, name: str = None, type: str = None,
    position: Vector3f = None, offset: Vector3f = None, min_distance: float = None,
    max_distance: float = None, sort: str = None, limit: int = None) -> List[EntityData]:
  """Gets a list of nearby entities and their attributes.

  Args:
    nbt: if `True`, populate an `"nbt"` attribute for each returned entity (optional)
    uuid: regular expression for matching entities' UUIDs (optional)
    name: regular expression for matching entities' names (optional)
    type: regular expression for matching entities' types (optional)
    position: position used with `offset`, `min_distance`, or `max_distance` to define a
        volume for filtering entities; default is the local player's position (optional)
    offset: offset relative to `position` for selecting entities (optional)
    min_distance: min distance relative to `position` for selecting entities (optional)
    max_distance: max distance relative to `position` for selecting entities (optional)
    sort: one of "nearest", "furthest", "random", or "arbitrary" (optional)
    limit: maximum number of entities to return (optional)

  Returns:
    `List[EntityData]` representing a snapshot of values for the selected entities.
    (Legacy returned dicts can be restored with `options.legacy_dict_return_values = True`)

  Update in v4.0:
    Added args: uuid, name, type, position, offset, min_distance, max_distance, sort, limit.
    Return `List[EntityData]` instead of `List[Dict[str, Any]]` by default.
    Added `uuid`, `id`, and `passengers` (only for entities with passengers) to returned entities.

  Update in v3.1:
    Added `"health"` and `"local"` attributes, and `nbt` arg to output `"nbt"`
    attribute.

  Since: v2.1
  """
  return __mcall__(
      "entities",
      [nbt, uuid, name, type, position, offset, min_distance, max_distance, sort, limit])

get_entities = entities  # Alias for scripts with `entities` variables.


def version_info() -> VersionInfo:
  """Gets version info for Minecraft, Minescript, mod loader, launcher, and OS.

  `minecraft_class_name` is the runtime class name of the main Minecraft class which may be
  obfuscated.

  Returns:
    `VersionInfo`

  Since: v4.0
  """
  return __mcall__("version_info", [])


def world_info() -> WorldInfo:
  """Gets world properties.

  If the current world is a multiplayer world loaded from the server list, then
  the returned `name` and `address` attributes are the values as they appear in
  the server list; otherwise `name` is the name of the locally saved world and
  `address` is `localhost`.

  `day_ticks` are the ticks associated with the day-night cycle.

  Renamed from `world_properties()` from v3.1.

  Returns:
    `WorldInfo`

  Since: v4.0
  """
  return __mcall__("world_info", [])


def getblock(x: int, y: int, z: int) -> str:
  """Gets the type of block at position (x, y, z).

  Alias: get_block(...)

  Args:
    x, y, z: position of block to get

  Returns:
    block type at (x, y, z) as a string
  """
  return __mcall__("getblock", [x, y, z])

get_block = getblock  # alias


def getblocklist(positions: List[List[int]]) -> List[str]:
  """Gets the types of block at the specified [x, y, z] positions.

  Alias: get_block_list(...)

  Args:
    list of positions as lists of x, y, z int coordinates, e.g. [[0, 0, 0], [0, 0, 1]]

  Returns:
    block types at given positions as list of strings

  Update in v4.0:
    Removed `done_callback` arg. Use `getblocklist.as_async(...)` for async execution.

  Since: v2.1
  """
  return __mcall__("getblocklist", [positions])

get_block_list = getblocklist  # alias


class BlockRegion:
  """Accessor for blocks within an axis-aligned bounding box.

  See `get_block_region(...)` for creating a `BlockRegion`.

  Since: v5.0
  """
  
  def __init__(self, min_pos: BlockPos, max_pos: BlockPos, blocks: Tuple[str, ...]):
    """Creates a block region between `min_pos` and `max_pos`, inclusive.
    
    Args:
      min_pos: minimum position of axis-aligned bounding box
      max_pos: maximum position of axis-aligned bounding box
      blocks: tuple of block type strings covering the volume of blocks between `min_pos` and
          `max_pos`, inclusive; given Lx, Ly, Lz lengths of the bounding box in x, y, and z
          dimensions, the first value in the tuple represents the block at the min_pos;
          the first Lx values represent the min y, z edge of the volume; the first Lx * Lz
          values represent the blocks in the min y plane of the volume; the last value represents
          the block at max_pos.
    """
    self.min_pos = min_pos
    self.max_pos = max_pos
    self.x_length = max_pos[0] - min_pos[0] + 1
    self.y_length = max_pos[1] - min_pos[1] + 1
    self.z_length = max_pos[2] - min_pos[2] + 1
    self.blocks = blocks

  def get_block(self, x: int, y: int, z: int) -> str:
    """Gets the type of block at position (x, y, z)."""
    return self.blocks[self.get_index(x, y, z)]

  def get_index(self, x: int, y: int, z: int) -> int:
    """Gets the index into `blocks` sequence for position (x, y, z)."""

    x_index = x - self.min_pos[0]
    y_index = y - self.min_pos[1]
    z_index = z - self.min_pos[2]

    if not (0 <= x_index < self.x_length and
            0 <= y_index < self.y_length and
            0 <= z_index < self.z_length):
      raise IndexError(
          f"Block position {(x, y, z)} out of bounds for BlockRegion covering {self.min_pos} to {self.max_pos}")

    return x_index + z_index * self.x_length + y_index * self.x_length * self.z_length


def get_block_region(pos1: BlockPos, pos2: BlockPos, safety_limit: bool = True) -> BlockRegion:
  """Gets the types of blocks in the axis-aligned bounding box between pos1 and pos2, inclusive.

  Args:
    pos1, pos2: opposing corners of an axis-aligned bounding box (aabb)
    safety_limit: if `True`, fail if requested volume spans more than 1600 chunks

  Returns:
    `BlockRegion` covering the requested volume of blocks.

  Since: v5.0
  """
  region = __mcall__("get_block_region", [pos1, pos2, safety_limit])
  return BlockRegion(region.min_pos, region.max_pos, region.blocks)


def screen_name() -> Union[str, None]:
  """Gets the current GUI screen name, if there is one.

  Returns:
    Name of current screen (str) or `None` if no GUI screen is being displayed.

  Since: v3.2
  """
  return __mcall__("screen_name", [])


def show_chat_screen(show: bool, prompt: str = None) -> bool:
  """Shows or hides the chat screen.

  Args:
    show: if `True`, show the chat screen; otherwise hide it
    prompt: if show is `True`, insert `prompt` into chat input box upon showing chat screen.

  Returns:
    `True` if chat screen was successfully shown (`show=True`) or hidden (`show=False`)

  Since: v4.0
  """
  return __mcall__("show_chat_screen", [show, prompt])


def append_chat_history(message: str):
  """Appends `message` to chat history, available via up and down arrows in chat.

  Since: v4.0
  """
  return __mcall__("append_chat_history", [message])


def chat_input() -> List[Union[str, int]]:
  """Gets state of chat input text.

  Returns:
    `[text, position]` where `text` is `str` and `position` is `int` cursor position within `text`

  Since: v4.0
  """
  return __mcall__("chat_input", [])


def set_chat_input(text: str = None, position: int = None, color: int = None):
  """Sets state of chat input text.

  Args:
    text: if specified, replace chat input text
    position: if specified, move cursor to this position within the chat input box
    color: if specified, set input text color, formatted as 0xRRGGBB

  Since: v4.0
  """
  return __mcall__("set_chat_input", [text, position, color])


def container_get_items() -> List[ItemStack]:
  """Gets all items in an open container (chest, furnace, etc. with slots).

  Returns:
    List of items if a container's contents are displayed; `None` otherwise.

  Since: v4.0
  """
  return __mcall__("container_get_items", [])


def player_look_at(x: float, y: float, z: float):
  """Rotates the camera to look at a position.

  Args:
    x: x position
    y: y position
    z: z position

  Since: v4.0
  """
  return __mcall__("player_look_at", [x, y, z])


def set_interval(callback: Callable[[], None], timer_millis: int, *args, **kwargs) -> int:
  listener_id = -1
  activation_time = _System.currentTimeMillis() + timer_millis

  def on_render(event):
    nonlocal activation_time, listener_id
    now = _System.currentTimeMillis()
    if now >= activation_time:
      try:
        call_finished = False
        callback(*args, **kwargs)
        call_finished = True
        activation_time = now + timer_millis
      finally:
        if not call_finished:
          remove_event_listener(listener_id)

  listener_id = add_event_listener("render", on_render)
  return listener_id


def set_timeout(callback: Callable[[], None], timer_millis: int, *args, **kwargs) -> int:
  """Sets a timeout for a callback to be executed after a specified amount of time.

  Args:
    callback: the callback function to execute
    timer_millis: the amount of time in milliseconds to wait before executing the callback
    *args: additional arguments to pass to the callback
    **kwargs: additional keyword arguments to pass to the callback

  Returns:
    The ID of the timeout listener.

  Since: v5.0
  """
  listener_id = -1
  activation_time = _System.currentTimeMillis() + timer_millis

  def on_render(event):
    nonlocal listener_id
    now = _System.currentTimeMillis()
    if now >= activation_time:
      try:
        callback(*args, **kwargs)
      finally:
        remove_event_listener(listener_id)

  listener_id = add_event_listener("render", on_render)
  return listener_id

class ManagedCallback:
  """Wrapper for managing callbacks passed to Java APIs.

  Example:
    ```
    callback = ManagedCallback(on_hud_render)
    HudRenderCallback.EVENT.register(HudRenderCallback(callback))

    # Cancel after 1 second (1000 milliseconds):
    set_timeout(callback.cancel, 1000)
    ```
  """

  def __init__(self, callback, cancel_on_exception=True, default_value=None):
    """Creates a managed callback.

    Args:
      callback: a callable function or object to manage
      cancel_on_exception: if the callback raises an exception, cancel the callback
      default_value: value to return immediately if callback is called after being canceled
    """
    self.callback = callback
    self.cancel_on_exception = cancel_on_exception
    self.default_value = default_value
    self.canceled = False
    self.listener = add_event_listener("tick", lambda e: None)

  def cancel(self):
    """Cancels the callback, returning `default_value` if it continues to be called."""
    self.canceled = True
    remove_event_listener(self.listener)

  def __call__(self, *args):
    """Calls this callback, checking for cancellation."""
    if self.canceled:
      return self.default_value
    try:
      return self.callback(*args)
    except Exception as e:
      Minescript.reportException(e)
      if self.cancel_on_exception:
        self.cancel()
      return self.default_value


Rotation = tuple
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


def combine_rotations(rot1: Rotation, rot2: Rotation) -> Rotation:
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

@dataclass
class _SleepRequest:
  seconds: float

@dataclass
class _EventRequest:
  timeout_seconds: float = None

class EventLoop:
  """An event loop for running asynchronous code that can react to events.

  The event loop allows scripts to wait for events or sleep without
  blocking the main thread, using async/await.

  Since: v5.0
  """

  def __init__(self):
    """Initializes a new EventLoop instance."""

    self._listeners = dict()  # Map from event name to event listener ID.
    self._event_request = None  # EventRequest
    self._event_timeout_id = None  # int
    self._queued_events = []  # Events queued while EventLoop is sleeping.
    self._async_handler = None  # Async function supplied by user via EventLoop.run().

  def run(self, async_function):
    """Runs an asynchronous function within this event loop.

    Args:
      async_function: an async function that takes this `EventLoop` instance as its
          only argument and returns a coroutine.

    Raises:
      RuntimeException: if `run()` has already been called for this event loop,
          or if `async_function` does not return a coroutine.
    """

    if self._async_handler is not None:
      raise _RuntimeException("EventLoop.run() already called for this event loop")

    async_handler = async_function(self)
    if not isinstance(async_handler, _Coroutine):
      raise _RuntimeException(
          f"EventLoop.run() expected an async function that returns a coroutine but got {type(async_handler)}")

    self._async_handler = async_handler

    try:
      yielded_value = self._async_handler.send(None)
    except StopIteration as stop:
      return self._stop()
    except Exception as e:
      Minescript.reportJobException(__script__.vars["job"], e)
      return self._stop()

    self._next_loop_iteration(yielded_value)

  def add_listener(self, event_type: str):
    """Adds a listener for the specified event type.

    Args:
      event_type: the type of event to listen for (e.g., "chat", "tick", "render").

    Returns:
      `True` if the listener was successfully added; `False` if a listener for
      this event type already exists.
    """

    if event_type in self._listeners:
      return False
    else:
      self._listeners[event_type] = add_event_listener(event_type, self._handle_event)
      return True

  def remove_listener(self, event_type: str):
    """Removes the listener for the specified event type.

    Args:
      event_type: the type of event to remove the listener for.

    Returns:
      `True` if the listener was successfully removed; `False` if no listener
      exists for this event type.
    """

    if event_type in self._listeners:
      remove_event_listener(self._listeners[event_type])
      del self._listeners[event_type]
      return True
    else:
      return False

  # asyncio.sleep() returns the option result passed to sleep(), but this sleep() returns
  # the list of events that fired while sleeping.
  def sleep(self, seconds: float):
    """Asynchronously sleeps for the given number of seconds.

    While sleeping, any events that fire will be queued and returned as a list
    when the sleep finishes.

    Args:
      seconds: the number of seconds to sleep.

    Returns:
      A list of events that occurred while sleeping.
    """

    events = yield _SleepRequest(seconds)
    return events
  
  def event(self, timeout_seconds: float = None):
    """Asynchronously waits for the next event to occur.

    Args:
      timeout_seconds: optional maximum time to wait for an event.

    Returns:
      The event that occurred, or `None` if the timeout was reached.
    """

    event = yield _EventRequest(timeout_seconds)
    return event
  
  def _handle_event(self, event):
    if self._event_request is None:
      self._queued_events.append(event)
    else:
      self._event_request = None
      if self._event_timeout_id is not None:
        remove_event_listener(self._event_timeout_id)
        self._event_timeout_id = None
      try:
        self._next_loop_iteration(self._async_handler.send(event))
      except StopIteration as stop:
        return self._stop()
      except Exception as e:
        Minescript.reportJobException(__script__.vars["job"], e)
        return self._stop()

  def _handle_event_timeout(self):
    if self._event_request is not None:
      self._event_request = None
      try:
        self._next_loop_iteration(self._async_handler.send(None))
      except StopIteration as stop:
        return self._stop()
      except Exception as e:
        Minescript.reportJobException(__script__.vars["job"], e)
        return self._stop()

  def _next_loop_iteration(self, yielded_value):
    def on_finish_sleep():
      events = self._queued_events
      self._queued_events = []
      try:
        self._next_loop_iteration(self._async_handler.send(events))
      except StopIteration as stop:
        return self._stop()
      except Exception as e:
        Minescript.reportJobException(__script__.vars["job"], e)
        return self._stop()

    if isinstance(yielded_value, _SleepRequest):
      set_timeout(on_finish_sleep, int(yielded_value.seconds * 1000))
    elif isinstance(yielded_value, _EventRequest):
      if yielded_value.timeout_seconds is not None:
        self._event_timeout_id = set_timeout(
            self._handle_event_timeout,
            int(yielded_value.timeout_seconds * 1000))
      self._event_request = yielded_value

  def _stop(self):
    for listener_id in self._listeners.values():
      remove_event_listener(listener_id)
    self._listeners.clear()
