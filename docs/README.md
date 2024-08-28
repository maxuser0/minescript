## Minescript v4.0 docs

Table of contents:

- [In-game commands](#in-game-commands)
    - [Command basics](#command-basics)
    - [Built-in commands](#built-in-commands)
    - [Advanced commands](#advanced-commands)
- [Configuration](#configuration)
- [Python API](#python-api)
    - [Script input](#script-input)
    - [Script output](#script-output)
    - [minescript module](#minescript-module)

## In-game commands

### Command basics

Minescript commands are available from the in-game chat console. They’re
similar to Minecraft commands that start with a slash (`/`), but Minescript
commands start with a backslash (`\`) instead.

Python scripts in the **minescript** folder (within the **minecraft** folder)
can be run as commands from the in-game chat by placing a backslash (`\`)
before the script name and dropping the `.py` from the end of the filename.
E.g. a Python script `minecraft/minescript/build_fortress.py` can be run as a
Minescript command by entering `\build_fortress` in the in-game chat. If the
in-game chat is hidden, you can display it by pressing `\`, similar to
pressing `t` or `/` in vanilla Minecraft. Parameters can be passed to
Minescript script commands if the Python script supports command-line
parameters. (See example at [Script input](#script-input) below).

Minescript commands that take a sequence of `X Y Z` parameters can take tilde
syntax to specify locations relative to the local player, e.g. `~ ~ ~` or `~-1
~2 ~-3`. Alternatively, params can be specified as `$x`, `$y`, and `$z` for the
local player’s x, y, or z coordinate, respectively. (`$` syntax is particularly
useful to specify coordinates that don’t appear as 3 consecutive `X Y Z`
coordinates.)

Optional command parameters below are shown in square brackets like this:
[EXAMPLE]. (But leave out the brackets when entering actual commands.)

### Built-in commands

#### ls
*Usage:* `\ls`

List all available Minescript commands, including both Minescript built-in
commands and Python scripts in the **minescript** folder.

#### help
*Usage:* `\help  NAME`

Prints documentation for the given script or command name.

Since: v1.19.2

#### eval
*Usage:* `\eval PYTHON_CODE [LINE2 [LINE3 ...]]`

Executes PYTHON_CODE (and optional subsequent lines LINE2, LINE3, etc) as
either a Python expression (code that can appear on the right-hand side of an
assignment, in which case the value is echoed to the chat screen) or Python
statements (e.g. a `for` loop).

Functions from [`minescript.py`](#minescript-module) are available automatically without
qualification.

*Examples:*

- Print information about nearby entities to the chat screen:

  ```
  \eval "entities()"
  ```

- Print the names of nearby entities to the chat screen:

  ```
  \eval "for e in entities(): echo(e['name'])"
  ```

- Import `time` module, sleep 3 seconds, and take a screenshot:

  ```
  \eval "import time" "time.sleep(3)" "screenshot()"
  ```

#### copy
*Usage:* `\copy  X1  Y1  Z1  X2  Y2  Z2  [LABEL] [no_limit]`

Copies blocks within the rectangular box from (X1, Y1, Z1) to (X2, Y2, Z2),
similar to the coordinates passed to the /fill command. LABEL is optional,
allowing a set of blocks to be named.

By default, attempts to copy a region covering more than 1600 chunks are
disallowed. This limit can be relaxed by passing `no_limit`.

See [\paste](#paste).

#### paste
*Usage:* `\paste  X  Y  Z  [LABEL]`

Pastes blocks at location (X, Y, Z) that were previously copied via \copy. When
the optional param LABEL is given, blocks are pasted from the most recent copy
command with the same LABEL given, otherwise blocks are pasted from the most
recent copy command with no label given.

Note that \copy and \paste can be run within different worlds to copy a region
from one world into another.

See [\copy](#copy).

#### jobs
*Usage:* `\jobs`

Lists the currently running Minescript jobs.

#### suspend
*Usage:* `\suspend  [JOB_ID]`

Suspends currently running Minescript job or jobs. If JOB_ID is specified, the
job with that integer ID is suspended. Otherwise, all currently running
Minescript jobs are suspended.

See [\resume](#resume).

#### z
*Usage:* `\z  [JOB_ID]`

Alias for `\suspend`.

#### resume
*Usage:* `\resume  [JOB_ID]`

Resumes currently suspended Minescript job or jobs. If JOB_ID is specified, the
job with that integer ID is resumed if currently suspended. If JOB_ID is not
specified, all currently suspended Minescript jobs are resumed.

See [\suspend](#suspend).

#### killjob
*Usage:* `\killjob  JOB_ID`

Kills the currently running or suspended Minescript job corresponding to
JOB_ID. The special value -1 can be specified to kill all currently running or
suspended Minescript jobs.

#### undo
*Usage:* `\undo`

Undoes all the `/setblock` and `/fill` commands run by the last Minescript
command by restoring the blocks present beforehand. This is useful if a
Minescript command accidentally destroyed a build and you’d like to revert to
the state of your build before the last command. `\undo` can be run multiple
times to undo the build changes from multiple recent Minescript commands.

***Note:*** *Some block state may be lost when undoing a Minescript command, such as
commands specified within command blocks and items in chests.*

### Advanced commands

#### minescript_commands_per_cycle
*Usage:* `\minescript_commands_per_cycle  NUMBER`

Specifies the number of Minescript-generated Minecraft commands to run per
Minescript processing cycle. The higher the number, the faster the script will
run.

***Note:*** *Setting this value too high will make Minecraft less responsive and
possibly crash.*

Default is 15.

#### minescript_ticks_per_cycle
*Usage:* `\minescript_ticks_per_cycle  NUMBER`

Specifies the number of Minecraft game ticks to wait per Minecraft processing
cycle. The lower the number, down to a minimum of 1, the faster the script will
run.

Default is 1 since v3.2. (Previously, default was 3.)

#### minescript_incremental_command_suggestions 
*Usage:* `\minescript_incremental_command_suggestions  BOOL`

Enables or disables printing of incremental command suggestions to the in-game
chat as the user types a Minescript command.

Default is false.

Since: v2.0 (in prior versions, incremental command suggestions were
unconditionally enabled)

## Configuration

The `minescript` directory contains a configuration file named `config.txt`.
Lines of text in `config.txt` can take the following forms:

- Lines containing configuration variables of the form: `NAME=VALUE`, e.g. `python="/usr/bin/python3"`.
- Lines ending with a backslash (`\`) are interpreted as being joined with the
  next line. Multiple consecutive lines ending in `\` are considered the same
  line of configuration together with the subsequent line.
- Lines beginning with `#` are comments that have no effect on Minescript behavior.
- Blank lines have no effect on Minescript behavior.

Config variable names:

- `python` - file location of the Python interpreter (default for Windows is
  `"%userprofile%\AppData\Local\Microsoft\WindowsApps\python3.exe"`, and
  `"/usr/bin/python3"` for other operating systems)
- `minescript_commands_per_cycle` (see [minescript_commands_per_cycle](#minescript_commands_per_cycle) command)
- `minescript_ticks_per_cycle` (see [minescript_ticks_per_cycle](#minescript_ticks_per_cycle) command)
- `minescript_incremental_command_suggestions` (see [minescript_incremental_command_suggestions](#minescript_incremental_command_suggestions) command; since v2.0)
- `report_job_success_threshold_millis` - report on-screen that a script job has exited successfully
  if it has run for more than this duration in milliseconds; default value is 3000 (3 seconds); 0
  always reports; -1 never reports; exits of failed script jobs are always reported (since v4.0)
- `autorun[WORLD NAME]` - command to run when entering a world named `WORLD NAME` (since v3.1)

    - The special name `*` indicates that the command should be run when entering
      all worlds, e.g. `autorun[*]=print_motd` where `print_motd.py` is a script
      that prints a "message of the day".
    - Multiple `autorun[...]` config lines can be specified for the same world, or
      for `*`, in which case all matching commands are run concurrently.
    - A single `autorun[...]` config line can execute multiple commands in
      sequence by separating commands with a semicolon (`;`), e.g. the following would
      first run the script `print_motd.py` followed by `summarize_entities.py` which takes
      a single argument (`50`):

      ```
      autorun[*]=print_motd; summarize_entities 50
      ```

## Python API

### Script input

Parameters can be passed from Minecraft as input to a Python script. For
example, consider this Python script located at
`minecraft/minescript/build_fortress.py`:

```
import sys

def BuildFortress(width, height, length):
  ...

width = sys.argv[1]
height = sys.argv[2]
length = sys.argv[3]
# Or more succinctly:
# width, height, length = sys.argv[1:]
BuildFortress(width, height, length)
```

The above script can be run from the Minecraft in-game chat as:

```
\build_fortress 100 50 200
```

That command passes parameters that set `width` to `100`, `height` to `50`, and
`length` to `200`.

### Script output

Minescript Python scripts can write outputs using `sys.stdout` and
`sys.stderr`, or they can use functions defined in `minescript.py` (see
[`echo`](#echo), [`chat`](#chat), and [`execute`](#execute)).  The
`minescript.py` functions are recommended going forward, but output via
`sys.stdout` and `sys.stderr` are provided for backward compatibility with
earlier versions of Minescript.

Printing to standard output (`sys.stdout`) outputs text to the Minecraft chat
as if entered by the user:

```
# Sends a chat message that's visible to
# all players in the world:
print("hi, friends!")

# Since Minescript v2.0 this can be written as:
import minescript
minescript.chat("hi, friends!")

# Runs a command to set the block under the
# current player to yellow concrete (assuming
# you have permission to run commands):
print("/setblock ~ ~-1 ~ yellow_concrete")

# Since Minescript v2.1 this can be written as:
minescript.execute("/setblock ~ ~-1 ~ yellow_concrete")
```

When a script prints to standard error (`sys.stderr`), the output text is
printed to the Minecraft chat, but is visible only to you:

```
# Prints a message to the in-game chat that's
# visible only to you:
print("Note to self...", file=sys.stderr)

# Since Minescript v2.0 this can be written as:
minescript.echo("Note to self...")
```

### minescript module
*Usage:* `import minescript  # from Python script`

User-friendly API for scripts to make function calls into the
Minescript mod.  This module should be imported by other
scripts and not run directly.

#### BlockPos
Tuple representing `(x: int, y: int, z: int)` position in block space.

#### Vector3f
Tuple representing `(x: float, y: float, z: float)` position or offset in 3D space.

#### execute
*Usage:* <code>execute(command: str, _as_task=False)</code>

Executes the given command.

If `command` is prefixed by a backslash, it's treated as Minescript command,
otherwise it's treated as a Minecraft command (the slash prefix is optional).

*Note: This was named `exec` in Minescript 2.0. The old name is no longer
available in v3.0.*

Since: v2.1


#### echo
*Usage:* <code>echo(\*messages, _as_task=False)</code>

Echoes plain-text messages to the chat.

Echoed messages are visible only to the local player.

If multiple args are given, join messages with a space separating them.

Update in v4.0:
  Support multiple plain-text messages.

Since: v2.0


#### echo_json
*Usage:* <code>echo_json(json_text)</code>

Echoes JSON-formatted text to the chat.

Echoed text is visible only to the local player.

`json_text` may be a string representing JSON text, or a list or dict. If it's a list or dict,
convert it to a JSON string using the standard `json` module.

Since: v4.0


#### chat
*Usage:* <code>chat(\*messages, _as_task=False)</code>

Sends messages to the chat.

If `messages[0]` is a str starting with a slash or backslash, automatically
prepends a space so that the messages are sent as a chat and not executed as
a command. If `len(messages)` is greater than 1, join messages with a space
separating them.  Ignores empty `messages`.

Update in v4.0:
  Support multiple messages.

Since: v2.0


#### log
*Usage:* <code>log(\*messages, _as_task=False)</code>

Sends messages to latest.log.

Update in v4.0:
  Support multiple messages of any type. Auto-convert messages to `str`.

Since: v3.0


#### screenshot
*Usage:* <code>screenshot(filename=None)</code>

Takes a screenshot, similar to pressing the F2 key.

*Args:*

- `filename`: if specified, screenshot filename relative to the screenshots directory; ".png"
    extension is added to the screenshot file if it doesn't already have a png extension.

Since: v2.1


#### job_info
*Usage:* <code>job_info() -> List[JobInfo]</code>

Return info about active Minescript jobs.

*Returns:*

- `JobInfo`.  For the  enclosing job, `JobInfo.self` is `True`.

Since: v4.0


#### flush
*Usage:* <code>flush()</code>

Wait for all previously issued script commands from this job to complete.

Since: v2.1


#### player_name
*Usage:* <code>player_name() -> str</code>

Gets the local player's name.

Since: v2.1


#### player_position
*Usage:* <code>player_position() -> List[float]</code>

Gets the local player's position.

*Returns:*

- player's position as [x: float, y: float, z: float]

Update in v4.0:
  Removed `done_callback` arg. Use `async_player_position()` for async execution.


#### player_hand_items
*Usage:* <code>player_hand_items() -> HandItems</code>

Gets the items in the local player's hands.

*Returns:*

- Items in player's hands.
  (Legacy-style return value can be restored with `options.legacy_dict_return_values = True`)

Update in v4.0:
  Return `HandItems` instead of `List[Dict[str, Any]]` by default.
  Removed `done_callback` arg. Use `async_player_hand_items()` for async execution.

Since: v2.0


#### player_inventory
*Usage:* <code>player_inventory() -> List[ItemStack]</code>

Gets the items in the local player's inventory.

*Returns:*

- Items in player's inventory.
  (Legacy-style return value can be restored with `options.legacy_dict_return_values = True`)

Update in v4.0:
  Return `List[ItemStack]` instead of `List[Dict[str, Any]]` by default.
  Removed `done_callback` arg. Use `async_player_inventory()` for async execution.

Update in v3.0:
  Introduced `"slot"` and `"selected"` attributes in the returned
  dict, and `"nbt"` is populated only when NBT data is present. (In prior
  versions, `"nbt"` was always populated, with a value of `null` when NBT data
  was absent.)

Since: v2.0


#### player_inventory_slot_to_hotbar
*Usage:* <code>player_inventory_slot_to_hotbar(slot: int) -> int</code>

Swaps an inventory item into the hotbar.

*Args:*

- `slot`: inventory slot (9 or higher) to swap into the hotbar

*Returns:*

- hotbar slot (0-8) into which the inventory item was swapped

Update in v4.0:
  Removed `done_callback` arg. Use `async_player_inventory_slot_to_hotbar(...)
  for async execution.

Since: v3.0


#### player_inventory_select_slot
*Usage:* <code>player_inventory_select_slot(slot: int) -> int</code>

Selects the given slot within the player's hotbar.

*Args:*

- `slot`: hotbar slot (0-8) to select in the player's hand

*Returns:*

- previously selected hotbar slot

Update in v4.0:
  Removed `done_callback` arg. Use `async_player_inventory_select_slot(...)` for async execution.

Since: v3.0


#### press_key_bind
*Usage:* <code>press_key_bind(key_mapping_name: str, pressed: bool)</code>

Presses/unpresses a mapped key binding.

*Args:*

- `key_mapping_name`: name of key binding, e.g. "key.hotbar.1"
- `pressed`: if `True`, press the bound key, otherwise unpress it

Since: v4.0


#### player_press_forward
*Usage:* <code>player_press_forward(pressed: bool)</code>

Starts/stops moving the local player forward, simulating press/release of the 'w' key.

*Args:*

- `pressed`: if `True`, go forward, otherwise stop doing so

Since: v2.1


#### player_press_backward
*Usage:* <code>player_press_backward(pressed: bool)</code>

Starts/stops moving the local player backward, simulating press/release of the 's' key.

*Args:*

- `pressed`: if `True`, go backward, otherwise stop doing so

Since: v2.1


#### player_press_left
*Usage:* <code>player_press_left(pressed: bool)</code>

Starts/stops moving the local player to the left, simulating press/release of the 'a' key.

*Args:*

- `pressed`: if `True`, move to the left, otherwise stop doing so

Since: v2.1


#### player_press_right
*Usage:* <code>player_press_right(pressed: bool)</code>

Starts/stops moving the local player to the right, simulating press/release of the 'd' key.

*Args:*

- `pressed`: if `True`, move to the right, otherwise stop doing so

Since: v2.1


#### player_press_jump
*Usage:* <code>player_press_jump(pressed: bool)</code>

Starts/stops the local player jumping, simulating press/release of the space key.

*Args:*

- `pressed`: if `True`, jump, otherwise stop doing so

Since: v2.1


#### player_press_sprint
*Usage:* <code>player_press_sprint(pressed: bool)</code>

Starts/stops the local player sprinting, simulating press/release of the left control key.

*Args:*

- `pressed`: if `True`, sprint, otherwise stop doing so

Since: v2.1


#### player_press_sneak
*Usage:* <code>player_press_sneak(pressed: bool)</code>

Starts/stops the local player sneaking, simulating press/release of the left shift key.

*Args:*

- `pressed`: if `True`, sneak, otherwise stop doing so

Since: v2.1


#### player_press_pick_item
*Usage:* <code>player_press_pick_item(pressed: bool)</code>

Starts/stops the local player picking an item, simulating press/release of the middle mouse button.

*Args:*

- `pressed`: if `True`, pick an item, otherwise stop doing so

Since: v2.1


#### player_press_use
*Usage:* <code>player_press_use(pressed: bool)</code>

Starts/stops the local player using an item or selecting a block, simulating press/release of the right mouse button.

*Args:*

- `pressed`: if `True`, use an item, otherwise stop doing so

Since: v2.1


#### player_press_attack
*Usage:* <code>player_press_attack(pressed: bool)</code>

Starts/stops the local player attacking or breaking a block, simulating press/release of the left mouse button.

*Args:*

- `pressed`: if `True`, press attack, otherwise stop doing so

Since: v2.1


#### player_press_swap_hands
*Usage:* <code>player_press_swap_hands(pressed: bool)</code>

Starts/stops moving the local player swapping hands, simulating press/release of the 'f' key.

*Args:*

- `pressed`: if `True`, swap hands, otherwise stop doing so

Since: v2.1


#### player_press_drop
*Usage:* <code>player_press_drop(pressed: bool)</code>

Starts/stops the local player dropping an item, simulating press/release of the 'q' key.

*Args:*

- `pressed`: if `True`, drop an item, otherwise stop doing so

Since: v2.1


#### player_orientation
*Usage:* <code>player_orientation()</code>

Gets the local player's orientation.

*Returns:*

- (yaw: float, pitch: float) as angles in degrees

Since: v2.1


#### player_set_orientation
*Usage:* <code>player_set_orientation(yaw: float, pitch: float)</code>

Sets the local player's orientation.

*Args:*

- `yaw`: degrees rotation of the local player's orientation around the y axis
- `pitch`: degrees rotation of the local player's orientation from the x-z plane

*Returns:*

- True if successful

Since: v2.1


#### player_get_targeted_block
*Usage:* <code>player_get_targeted_block(max_distance: float = 20)</code>

Gets info about the nearest block, if any, in the local player's crosshairs.

*Args:*

- `max_distance`: max distance from local player to look for blocks

*Returns:*

- `TargetedBlock` for the block targeted by the player, or `None` if no block is targeted.

Update in v4.0:
  Return value changed from `list` to `TargetedBlock`.

Since: v3.0


#### player_get_targeted_entity
*Usage:* <code>player_get_targeted_entity(max_distance: float = 20, nbt: bool = False) -> EntityData</code>

Gets the entity targeted in the local player's crosshairs, if any.

*Args:*

- `max_distance`: maximum distance to check for targeted entities
- `nbt`: if `True`, populate an `"nbt"` attribute for the player

*Returns:*

- `EntityData` for the entity targeted by the player, or `None` if no entity is targeted.
  (Legacy-style returned dict can be restored with `options.legacy_dict_return_values = True`)

Since: v4.0


#### player_health
*Usage:* <code>player_health() -> float</code>

Gets the local player's health.

Since: v3.1


#### player
*Usage:* <code>player(\*, nbt: bool = False)</code>

Gets attributes for the local player.

*Args:*

- `nbt`: if `True`, populate the `nbt` field for the player

*Returns:*

- `EntityData` representing a snapshot of values for the local player.
  (Legacy-style returned dict can be restored with `options.legacy_dict_return_values = True`)

Since: v4.0


#### players
*Usage:* <code>players(\*, nbt: bool = False, uuid: str = None, name: str = None, position: [Vector3f](#vector3f) = None, offset: [Vector3f](#vector3f) = None, min_distance: float = None, max_distance: float = None, sort: str = None, limit: int = None)</code>

Gets a list of nearby players and their attributes.

*Args:*

- `nbt`: if `True`, populate an `"nbt"` attribute for each returned player
- `uuid`: regular expression for matching entities' UUIDs (optional)
- `name`: regular expression for matching entities' names (optional)
- `position`: position used with `offset`, `min_distance`, or `max_distance` to define a
      volume for filtering entities; default is the local player's position (optional)
- `offset`: offset relative to `position` for selecting entities (optional)
- `min_distance`: min distance relative to `position` for selecting entities (optional)
- `max_distance`: max distance relative to `position` for selecting entities (optional)
- `sort`: one of "nearest", "furthest", "random", or "arbitrary" (optional)
- `limit`: maximum number of entities to return (optional)

*Returns:*

- `List[EntityData]` representing a snapshot of values for the selected players.
  (Legacy returned dicts can be restored with `options.legacy_dict_return_values = True`)

Update in v4.0:
  Added args: uuid, name, type, position, offset, min_distance, max_distance, sort, limit.
  Return `List[EntityData]` instead of `List[Dict[str, Any]]` by default.
  Added `uuid` and `id` to returned players.

Update in v3.1:
  Added `"health"` and `"local"` attributes, and `nbt` arg to output `"nbt"`
  attribute.

Since: v2.1


#### entities
*Usage:* <code>entities(\*, nbt: bool = False, uuid: str = None, name: str = None, type: str = None, position: [Vector3f](#vector3f) = None, offset: [Vector3f](#vector3f) = None, min_distance: float = None, max_distance: float = None, sort: str = None, limit: int = None)</code>

Gets a list of nearby entities and their attributes.

*Args:*

- `nbt`: if `True`, populate an `"nbt"` attribute for each returned entity (optional)
- `uuid`: regular expression for matching entities' UUIDs (optional)
- `name`: regular expression for matching entities' names (optional)
- `type`: regular expression for matching entities' types (optional)
- `position`: position used with `offset`, `min_distance`, or `max_distance` to define a
      volume for filtering entities; default is the local player's position (optional)
- `offset`: offset relative to `position` for selecting entities (optional)
- `min_distance`: min distance relative to `position` for selecting entities (optional)
- `max_distance`: max distance relative to `position` for selecting entities (optional)
- `sort`: one of "nearest", "furthest", "random", or "arbitrary" (optional)
- `limit`: maximum number of entities to return (optional)

*Returns:*

- `List[EntityData]` representing a snapshot of values for the selected entities.
  (Legacy returned dicts can be restored with `options.legacy_dict_return_values = True`)

Update in v4.0:
  Added args: uuid, name, type, position, offset, min_distance, max_distance, sort, limit.
  Return `List[EntityData]` instead of `List[Dict[str, Any]]` by default.
  Added `uuid`, `id`, and `passengers` (only for entities with passengers) to returned entities.

Update in v3.1:
  Added `"health"` and `"local"` attributes, and `nbt` arg to output `"nbt"`
  attribute.

Since: v2.1


#### version_info
*Usage:* <code>version_info() -> VersionInfo</code>

Gets version info for Minecraft, Minescript, mod loader, launcher, and OS.

*Returns:*

- `VersionInfo`

Since: v4.0


#### world_info
*Usage:* <code>world_info() -> WorldInfo</code>

Gets world properties.

If the current world is a multiplayer world loaded from the server list, then
the returned `name` and `address` attributes are the values as they appear in
the server list; otherwise `name` is the name of the locally saved world and
`address` is `localhost`.

`day_ticks` are the ticks associated with the day-night cycle.

Renamed from `world_properties()` from v3.1.

*Returns:*

- `WorldInfo`

Since: v4.0


#### getblock
*Usage:* <code>getblock(x: int, y: int, z: int) -> str</code>

Gets the type of block at position (x, y, z).

*Args:*

- `x, y, z`: position of block to get

*Returns:*

- block type at (x, y, z) as a string


#### getblocklist
*Usage:* <code>getblocklist(positions: List[List[int]]) -> List[str]</code>

Gets the types of block at the specified [x, y, z] positions.

*Args:*

  list of positions as lists of x, y, z int coordinates, e.g. [[0, 0, 0], [0, 0, 1]]

*Returns:*

- block types at given positions as list of strings

Update in v4.0:
  Removed `done_callback` arg. Use `async_getblocklist(...)` for async execution.

Since: v2.1


#### await_loaded_region
*Usage:* <code>await_loaded_region(x1: int, z1: int, x2: int, z2: int)</code>

Waits for chunks to load in the region from (x1, z1) to (x2, z2).

*Args:*

- `x1, z1, x2, z2`: bounds of the region for awaiting loaded chunks
- `timeout`: if specified, timeout in seconds to wait for the region to load

Update in v4.0:
  Removed `done_callback` arg. Call now always blocks until region is loaded.


#### KeyEvent
Key event data.

For a list of key codes, see: https://www.glfw.org/docs/3.4/group__keys.html
`action` is 0 for key up, 1 for key down, and 2 for key repeat.


#### MouseEvent
Mouse event data.

`action` is 0 for mouse up and 1 for mouse down.


#### EventQueue
Queue for managing events.

Implements context management so that it can be used with a `with` expression
to automatically unregister event listeners at the end of the block, e.g.

```
with EventQueue() as event_queue:
  echo("Capturing key events...")
  event_queue.register_key_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.KEY:
      # Key code 93 is the `]` key.
      if event.key == 93:
        break
      echo(f"Captured key with code {event.key}.")

echo("No longer capturing key events.")
```

Since: v4.0


#### EventQueue.\_\_init\_\_
*Usage:* <code>EventQueue()</code>

Creates an event registration handler.

#### EventQueue.get
*Usage:* <code>EventQueue.get(block: bool = True, timeout: float = None) -> Any</code>

Gets the next event in the queue.

*Args:*

- `block`: if `True`, block until an event fires
- `timeout`: timeout in seconds to wait for an event if `block` is `True`

*Returns:*

- subclass-dependent event

*Raises:*

  `queue.Empty` if `block` is `True` and `timeout` expires, or `block` is `False` and
  queue is empty.


#### KeyEventListener
*Usage:* <code>KeyEventListener()</code>

Deprecated listener for keyboard events. Use `EventQueue.register_key_listener` instead.

Update in v4.0:
  Deprecated in favor of `EventQueue.register_key_listener`.

Since: v3.2


#### ChatEventListener
*Usage:* <code>ChatEventListener()</code>

Deprecated listener for chat message events.

Use `EventQueue.register_chat_message_listener` instead.

Update in v4.0:
  Deprecated in favor of `EventQueue.register_chat_message_listener`.

Since: v3.2


#### screen_name
*Usage:* <code>screen_name() -> str</code>

Gets the current GUI screen name, if there is one.

*Returns:*

- Name of current screen (str) or `None` if no GUI screen is being displayed.

Since: v3.2


#### show_chat_screen
*Usage:* <code>show_chat_screen(show: bool, prompt: str = None) -> str</code>

Shows or hides the chat screen.

*Args:*

- `show`: if `True`, show the chat screen; otherwise hide it
- `prompt`: if show is `True`, insert `prompt` into chat input box upon showing chat screen.

*Returns:*

- `True` if chat screen was successfully shown (`show=True`) or hidden (`show=False`)

Since: v4.0


#### append_chat_history
*Usage:* <code>append_chat_history(message: str)</code>

Appends `message` to chat history, available via up and down arrows in chat.

Since: v4.0


#### chat_input
*Usage:* <code>chat_input()</code>

Gets state of chat input text.

*Returns:*

- `[text, position]` where `text` is `str` and `position` is `int` cursor position within `text`

Since: v4.0


#### set_chat_input
*Usage:* <code>set_chat_input(text: str = None, position: int = None, color: int = None)</code>

Sets state of chat input text.

*Args:*

- `text`: if specified, replace chat input text
- `position`: if specified, move cursor to this position within the chat input box
- `color`: if specified, set input text color, formatted as 0xRRGGBB

Since: v4.0


#### container_get_items
*Usage:* <code>container_get_items() -> List[ItemStack]</code>

Gets all items in an open container (chest, furnace, etc. with slots).

*Returns:*

- List of items if a container's contents are displayed; `None` otherwise.

Since: v4.0


#### player_look_at
*Usage:* <code>player_look_at(x: float, y: float, z: float)</code>

Rotates the camera to look at a position.

*Args:*

- `x`: x position
- `y`: y position
- `z`: z position

Since: v4.0


#### Rotation
Tuple of 9 `int` values representing a flattened, row-major 3x3 rotation matrix.

#### Rotations
Common rotations for use with [`BlockPack`](#blockpack) and [`BlockPacker`](#blockpacker) methods.

Since: v3.0


#### Rotations.IDENTITY
Effectively no rotation.

#### Rotations.X_90
Rotate 90 degrees about the x axis.

#### Rotations.X_180
Rotate 180 degrees about the x axis.

#### Rotations.X_270
Rotate 270 degrees about the x axis.

#### Rotations.Y_90
Rotate 90 degrees about the y axis.

#### Rotations.Y_180
Rotate 180 degrees about the y axis.

#### Rotations.Y_270
Rotate 270 degrees about the y axis.

#### Rotations.Z_90
Rotate 90 degrees about the z axis.

#### Rotations.Z_180
Rotate 180 degrees about the z axis.

#### Rotations.Z_270
Rotate 270 degrees about the z axis.

#### Rotations.INVERT_X
Invert the x coordinate (multiply by -1).

#### Rotations.INVERT_Y
Invert the y coordinate (multiply by -1).

#### Rotations.INVERT_Z
Invert the z coordinate (multiply by -1).

#### combine_rotations
*Usage:* <code>combine_rotations(rot1: [Rotation](#rotation), rot2: [Rotation](#rotation), /) -> [Rotation](#rotation)</code>

Combines two rotation matrices into a single rotation matrix.

Since: v3.0


#### BlockPack
BlockPack is an immutable and serializable collection of blocks.

A blockpack can be read from or written to worlds, files, and serialized
bytes. Although blockpacks are immutable and preserve position and
orientation of blocks, they can be rotated and offset when read from or
written to worlds.

For a mutable collection of blocks, see [`BlockPacker`](#blockpacker).

Since: v3.0


#### BlockPack.read_world
*Usage:* <code>@classmethod BlockPack.read_world(pos1: [BlockPos](#blockpos), pos2: [BlockPos](#blockpos), \*, rotation: [Rotation](#rotation) = None, offset: [BlockPos](#blockpos) = None, comments: Dict[str, str] = {}, safety_limit: bool = True) -> [BlockPack](#blockpack)</code>

Creates a blockpack from blocks in the world within a rectangular volume.

*Args:*

- `pos1, pos2`: opposing corners of a rectangular volume from which to read world blocks
- `rotation`: rotation matrix to apply to block coordinates read from world
- `offset`: offset to apply to block coordiantes (applied after rotation)
- `comments`: key, value pairs to include in the new blockpack
- `safety_limit`: if `True`, fail if requested volume spans more than 1600 chunks

*Returns:*

- a new BlockPack containing blocks read from the world


#### BlockPack.read_file
*Usage:* <code>@classmethod BlockPack.read_file(filename: str, \*, relative_to_cwd=False) -> [BlockPack](#blockpack)</code>

Reads a blockpack from a file.

*Args:*

- `filename`: name of file relative to minescript/blockpacks dir unless it's an absolute path
    (".zip" is automatically appended to filename if it does not end with that extension)
- `relative_to_cwd`: if `True`, relative filename is taken to be relative to Minecraft dir

*Returns:*

- a new BlockPack containing blocks read from the file


#### BlockPack.import_data
*Usage:* <code>@classmethod BlockPack.import_data(base64_data: str) -> [BlockPack](#blockpack)</code>

Creates a blockpack from base64-encoded serialized blockpack data.

*Args:*

- `base64_data`: base64-encoded string containing serialization of blockpack data.

*Returns:*

- a new BlockPack containing blocks read from the base64-encoded data


#### BlockPack.block_bounds
*Usage:* <code>BlockPack.block_bounds() -> (BlockPos, BlockPos)</code>

Returns min and max bounding coordinates of blocks in this BlockPack.

#### BlockPack.comments
*Usage:* <code>BlockPack.comments() -> Dict[str, str]</code>

Returns comments stored in this BlockPack.

#### BlockPack.write_world
*Usage:* <code>BlockPack.write_world(\*, rotation: [Rotation](#rotation) = None, offset: [BlockPos](#blockpos) = None)</code>

Writes blocks from this BlockPack into the current world. Requires setblock, fill commands.

*Args:*

- `rotation`: rotation matrix to apply to block coordinates before writing to world
- `offset`: offset to apply to block coordiantes (applied after rotation)


#### BlockPack.write_file
*Usage:* <code>BlockPack.write_file(filename: str, \*, relative_to_cwd=False)</code>

Writes this BlockPack to a file.

*Args:*

- `filename`: name of file relative to minescript/blockpacks dir unless it's an absolute path
    (".zip" is automatically appended to filename if it does not end with that extension)
- `relative_to_cwd`: if `True`, relative filename is taken to be relative to Minecraft dir


#### BlockPack.export_data
*Usage:* <code>BlockPack.export_data() -> str</code>

Serializes this BlockPack into a base64-encoded string.

*Returns:*

- a base64-encoded string containing this blockpack's data


#### BlockPack.\_\_del\_\_
*Usage:* <code>del blockpack</code>

Frees this BlockPack to be garbage collected.

#### BlockPacker
BlockPacker is a mutable collection of blocks.

Blocks can be added to a blockpacker by calling [`setblock(...)`](#blockpackersetblock), [`fill(...)`](#blockpackerfill),
and/or [`add_blockpack(...)`](#blockpackeradd_blockpack).  To serialize blocks or write them to a world, a
blockpacker can be "packed" by calling pack() to create a compact snapshot of
the blocks contained in the blockpacker in the form of a new BlockPack. A
blockpacker continues to store the same blocks it had before being packed,
and more blocks can be added thereafter.

For a collection of blocks that is immutable and serializable, see [`BlockPack`](#blockpack).

Since: v3.0


#### BlockPacker.\_\_init\_\_
*Usage:* <code>BlockPacker()</code>

Creates a new, empty blockpacker.

#### BlockPacker.setblock
*Usage:* <code>BlockPacker.setblock(pos: [BlockPos](#blockpos), block_type: str)</code>

Sets a block within this BlockPacker.

*Args:*

- `pos`: position of a block to set
- `block_type`: block descriptor to set

*Raises:*

  `BlockPackerException` if blockpacker operation fails


#### BlockPacker.fill
*Usage:* <code>BlockPacker.fill(pos1: [BlockPos](#blockpos), pos2: [BlockPos](#blockpos), block_type: str)</code>

Fills blocks within this BlockPacker.

*Args:*

- `pos1, pos2`: coordinates of opposing corners of a rectangular volume to fill
- `block_type`: block descriptor to fill

*Raises:*

  `BlockPackerException` if blockpacker operation fails


#### BlockPacker.add_blockpack
*Usage:* <code>BlockPacker.add_blockpack(blockpack: [BlockPack](#blockpack), \*, rotation: [Rotation](#rotation) = None, offset: [BlockPos](#blockpos) = None)</code>

Adds the blocks within a BlockPack into this BlockPacker.

*Args:*

- `blockpack`: BlockPack from which to copy blocks
- `rotation`: rotation matrix to apply to block coordinates before adding to blockpacker
- `offset`: offset to apply to block coordiantes (applied after rotation)


#### BlockPacker.pack
*Usage:* <code>BlockPacker.pack(\*, comments: Dict[str, str] = {}) -> [BlockPack](#blockpack)</code>

Packs blocks within this BlockPacker into a new BlockPack.

*Args:*

- `comments`: key, value pairs to include in the new BlockPack

*Returns:*

- a new BlockPack containing a snapshot of blocks from this BlockPacker


#### BlockPacker.\_\_del\_\_
*Usage:* <code>del blockpacker</code>

Frees this BlockPacker to be garbage collected.

#### java_class
*Usage:* <code>java_class(name: str)</code>

Looks up Java class by fully qualified name. Returns handle to Java object.

#### java_string
*Usage:* <code>java_string(s)</code>

Creates Java String. Returns handle to Java object.

#### java_double
*Usage:* <code>java_double(d)</code>

Creates Java Double. Returns handle to Java object.

#### java_float
*Usage:* <code>java_float(f)</code>

Creates Java Float. Returns handle to Java object.

#### java_long
*Usage:* <code>java_long(l)</code>

Creates Java Long. Returns handle to Java object.

#### java_int
*Usage:* <code>java_int(i)</code>

Creates Java Integer. Returns handle to Java object.

#### java_bool
*Usage:* <code>java_bool(b)</code>

Creates Java Boolean. Returns handle to Java object.

#### java_ctor
*Usage:* <code>java_ctor(clss)</code>

Returns handle to constructor for `clss`.

#### java_new_instance
*Usage:* <code>java_new_instance(ctor, \*args)</code>

Creates new Java instance. Returns handle to newly created Java object.

*Args:*

- `ctor`: constructor set returned from [`java_ctor`](#java_ctor)
- `args`: handles to Java objects to pass as constructor params


#### java_member
*Usage:* <code>java_member(clss, name: str)</code>

Gets Java member(s) matching `name`. Returns handle to Java object.

#### java_access_field
*Usage:* <code>java_access_field(target, field)</code>

Accesses `field` on `target`. Returns handle to Java object, or `None` if `null`.

#### java_call_method
*Usage:* <code>java_call_method(target, method, \*args)</code>

Invokes method on target. Returns handle to Java object, or `None` if `null`.

*Args:*

- `args`: handles to Java objects to pass as constructor params


#### java_array_length
*Usage:* <code>java_array_length(array)</code>

Returns length of array handle as `int`.

#### java_array_index
*Usage:* <code>java_array_index(array, i)</code>

Gets element `i` of array handle. Returns handle to Java object, or `None` if `null`.

#### java_to_string
*Usage:* <code>java_to_string(target)</code>

Returns `str` from calling `target.toString()` in Java.

#### java_assign
*Usage:* <code>java_assign(dest, source)</code>

Reassigns `dest` handle to reference the object referenced by `source` handle.

#### java_release
*Usage:* <code>java_release(\*targets)</code>

Releases the Java reference(s) associated with `targets`.

