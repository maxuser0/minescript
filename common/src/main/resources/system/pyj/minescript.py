# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
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

  Args:
    x, y, z: position of block to get

  Returns:
    block type at (x, y, z) as a string
  """
  return __mcall__("getblocks", [x, y, z])


def getblocklist(positions: List[List[int]]) -> List[str]:
  """Gets the types of block at the specified [x, y, z] positions.

  Args:
    list of positions as lists of x, y, z int coordinates, e.g. [[0, 0, 0], [0, 0, 1]]

  Returns:
    block types at given positions as list of strings

  Update in v4.0:
    Removed `done_callback` arg. Use `getblocklist.as_async(...)` for async execution.

  Since: v2.1
  """
  return __mcall__("getblocklist", [positions])


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
