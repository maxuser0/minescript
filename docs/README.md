## Minescript v3.2 docs

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

Previous version: [v3.1](v3.1/README.md)

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

#### execute
*Usage:* <code>execute(command: str)</code>

Executes the given command.

If `command` doesn't already start with a slash or backslash, automatically
prepends a slash. Ignores leading and trailing whitespace, and ignores empty
commands.

*Note: This was named `exec` in Minescript 2.0. The old name is no longer
available in v3.0.*

Since: v2.1


#### echo
*Usage:* <code>echo(message: Any)</code>

Echoes message to the chat.

The echoed message is visible only to the local player.

Since: v2.0


#### chat
*Usage:* <code>chat(message: str)</code>

Sends the given message to the chat.

If `message` starts with a slash or backslash, automatically prepends a space
so that the message is sent as a chat and not executed as a command.  Ignores
empty messages.

Since: v2.0


#### log
*Usage:* <code>log(message: str) -> bool</code>

Sends the given message to latest.log.

*Args:*

- `message`: string to send to the log

*Returns:*

- `True` if `message` was logged successfully.

Since: v3.0


#### screenshot
*Usage:* <code>screenshot(filename=None) -> bool</code>

Takes a screenshot, similar to pressing the F2 key.

*Args:*

- `filename`: if specified, screenshot filename relative to the screenshots directory; ".png"
    extension is added to the screenshot file if it doesn't already have a png extension.

*Returns:*

- `True` is successful

Since: v2.1


#### flush
*Usage:* <code>flush()</code>

Wait for all previously issued script commands from this job to complete.

Since: v2.1


#### player_name
*Usage:* <code>player_name() -> str</code>

Gets the local player's name.

Since: v2.1


#### player_position
*Usage:* <code>player_position(done_callback=None) -> List[float]</code>

Gets the local player's position.

*Args:*

- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- if `done_callback` is `None`, returns player's position as [x: float, y: float, z: float]


#### player_set_position
*Usage:* <code>player_set_position(x: float, y: float, z: float, yaw: float = None, pitch: float = None) -> bool</code>

Sets the player's position, and optionally orientation.

Note that in survival mode the server may reject the new coordinates if they're too far
or require moving through walls.

*Args:*

- `x, y, z`: position to try to move player to
- `yaw, pitch`: if not None, player's new orientation

Since: v3.1


#### player_hand_items
*Usage:* <code>player_hand_items(done_callback=None) -> List[Dict[str, Any]]</code>

Gets the items in the local player's hands.

*Args:*

- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- If `done_callback` is `None`, returns items in player's hands as a list of
- `items where each item is a dict`: `{"item": str, "count": int}`, plus
  `"nbt": str` if the item has NBT data; main-hand item is at list index 0,
  off-hand item at index 1.

Since: v2.0


#### player_inventory
*Usage:* <code>player_inventory(done_callback=None) -> List[Dict[str, Any]]</code>

Gets the items in the local player's inventory.

*Args:*

- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- If `done_callback` is `None`, returns items in player's inventory as list
- `of items where each item is a dict`: `{"item": str, "count": int, "slot":
  int}`, plus `"nbt": str` if an item has NBT data and `"selected": True` for
  the item selected in the player's main hand.

Update in v3.0:
  Introduced `"slot"` and `"selected"` attributes in the returned
  dict, and `"nbt"` is populated only when NBT data is present. (In prior
  versions, `"nbt"` was always populated, with a value of `null` when NBT data
  was absent.)

Since: v2.0


#### player_inventory_slot_to_hotbar
*Usage:* <code>player_inventory_slot_to_hotbar(slot: int, done_callback=None) -> int</code>

Swaps an inventory item into the hotbar.

*Args:*

- `slot`: inventory slot (9 or higher) to swap into the hotbar
- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- If `done_callback` is `None`, returns the hotbar slot (0-8) that the inventory
  item was swapped into

Since: v3.0


#### player_inventory_select_slot
*Usage:* <code>player_inventory_select_slot(slot: int, done_callback=None) -> int</code>

Selects the given slot within the player's hotbar.

*Args:*

- `slot`: hotbar slot (0-8) to select in the player's hand
- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- If `done_callback` is `None`, returns the previously selected hotbar slot

Since: v3.0


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

- [[x, y, z], distance, side, block_description] if the local player has a
  block in their crosshairs within `max_distance`, `None` otherwise.
  `distance` (float) is calculated from the player to the targeted block;
  `side` (str) is the direction that the targeted side of the block is facing
  (e.g. `"east"`); `block_description` (str) describes the targeted block.

Since: v3.0


#### player_health
*Usage:* <code>player_health() -> float</code>

Gets the local player's health.

Since: v3.1


#### players
*Usage:* <code>players(\*, nbt: bool = False)</code>

Gets a list of nearby players and their attributes.

*Args:*

- `nbt`: if `True`, populate an `"nbt"` attribute for each returned player

*Returns:*

- List of players where each player is represented as a dict containing:
  `"name": str, "health": float, "type": str,
  "position": [float, float, float], "yaw": float, "pitch": float,
  "velocity": [float, float, float]`. The local player has the attribute
  `"local": True`. The`"nbt"` attribute is present if `nbt` arg is `True`.

Update in v3.1:
  Added `"health"` and `"local"` attributes, and `nbt` arg to output `"nbt"`
  attribute.

Since: v2.1


#### entities
*Usage:* <code>entities(\*, nbt: bool = False)</code>

Gets a list of nearby entities and their attributes.

*Args:*

- `nbt`: if `True`, populate an `"nbt"` attribute for each returned entity

*Returns:*

- List of entities where each entity is represented as a dict containing:
  `"name": str, "health": float (living entities only), "type": str,
  "position": [float, float, float], "yaw": float, "pitch": float,
  "velocity": [float, float, float]`. Living entities have
  `"health": float` and the local player has `"local": True`. The`"nbt"`
  attribute is present if `nbt` arg is `True`.

Update in v3.1:
  Added `"health"` and `"local"` attributes, and `nbt` arg to output `"nbt"`
  attribute.

Since: v2.1


#### world_properties
*Usage:* <code>world_properties() -> Dict[str, Any]</code>

Gets world properties.

If the current world is a multiplayer world loaded from the server list, then
the returned `name` and `address` attributes are the values as they appear in
the server list; otherwise `name` is the name of the locally saved world and
`address` is `localhost`.

`"day_ticks"` are the ticks associated with the day-night cycle.

*Returns:*

- Dict containing: `"game_ticks": int, "day_ticks": int, "raining": bool,
  "thundering": bool, "spawn": BlockPos, "hardcore": bool,
  "difficulty": str, "name": str, "address": str`

Since: v3.1


#### getblock
*Usage:* <code>getblock(x: int, y: int, z: int, done_callback=None)</code>

Gets the type of block at position (x, y, z).

*Args:*

- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- if `done_callback` is `None`, returns the block type at (x, y, z) as a string


#### getblocklist
*Usage:* <code>getblocklist(positions: List[List[int]], done_callback=None)</code>

Gets the types of block at the specified [x, y, z] positions.

*Args:*

- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- if `done_callback` is `None`, returns the block types at given positions as list of strings

Since: v2.1


#### await_loaded_region
*Usage:* <code>await_loaded_region(x1: int, z1: int, x2: int, z2: int, done_callback=None)</code>

Notifies the caller when the region from (x1, z1) to (x2, z2) is loaded.

*Args:*

- `done_callback`: if given, return immediately and call `done_callback(return_value)`
      asynchronously when `return_value` is ready

*Returns:*

- if `done_callback` is `None`, returns `True` when the requested region is fully loaded.

*Examples:*

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


#### KeyEventListener
Listener for keyboard events.

Only one [`KeyEventListener`](#keyeventlistener) can be instantiated at a time within a job. For a
list of key codes, see: https://www.glfw.org/docs/3.4/group__keys.html

Since: v3.2


#### KeyEventListener.\_\_init\_\_
*Usage:* <code>KeyEventListener()</code>

Creates a [`KeyEventListener`](#keyeventlistener) for listening to keyboard events.

#### KeyEventListener.get
*Usage:* <code>KeyEventListener.get(block: bool = True, timeout: float = None) -> str</code>

Gets the next key event in the queue.

*Args:*

- `block`: if `True`, block until an event fires
- `timeout`: timeout in seconds to wait for an event if `block` is `True`

*Returns:*

- event dict: `{"key": int, "scanCode": int, "action": int, "modifiers": int,
  "timeMillis": int, "screen": str}` where `action` is 0 for key up, 1 for
  key down, and 2 for key repeat.

*Raises:*

  `queue.Empty` if `block` is `True` and `timeout` expires, or `block` is `False` and
  queue is empty.


#### ChatEventListener
Listener for chat message events.

Only one [`ChatEventListener`](#chateventlistener) can be instantiated at a time within a job.

Listener receives both incoming and outgoing chat messages.

Since: v3.2


#### ChatEventListener.\_\_init\_\_
*Usage:* <code>ChatEventListener()</code>

Creates a [`ChatEventListener`](#chateventlistener) to listen for chat messages.

#### ChatEventListener.get
*Usage:* <code>ChatEventListener.get(block: bool = True, timeout: float = None) -> str</code>

Gets the next chat event in the queue.

*Args:*

- `block`: if `True`, block until an event fires
- `timeout`: timeout in seconds to wait for an event if `block` is `True`

*Returns:*

- message from chat (str)

*Raises:*

  `queue.Empty` if `block` is `True` and `timeout` expires, or `block` is `False` and
  queue is empty.


#### register_chat_message_listener
*Usage:* <code>register_chat_message_listener(listener: Callable[[str], None], exception_handler: ExceptionHandler = None)</code>

Registers a listener for receiving chat messages. One listener allowed per job.

Listener receives both incoming and outgoing chat messages.

For a more user-friendly API, use [`ChatEventListener`](#chateventlistener) instead.

*Args:*

- `listener`: callable that repeatedly accepts a string representing chat messages
- `exception_handler`: callable for handling an `Exception` thrown from Java (optional)

Update in v3.2:
  Added optional arg `exception_handler`.

Since: v2.0

See also:
  [`register_chat_message_interceptor()`](#register_chat_message_interceptor) for swallowing outgoing chat messages


#### unregister_chat_message_listener
*Usage:* <code>unregister_chat_message_listener()</code>

Unregisters a chat message listener, if any, for the currently running job.

For a more user-friendly API, use [`ChatEventListener`](#chateventlistener) instead.

*Returns:*

- `True` if successfully unregistered a listener.

Since: v2.0


#### register_chat_message_interceptor
*Usage:* <code>register_chat_message_interceptor(interceptor: Callable[[str], None])</code>

Registers an interceptor for swallowing chat messages.

An interceptor swallows outgoing chat messages, typically for use in
rewriting outgoing chat messages by calling minecraft.chat(str), e.g. to
decorate or post-process outgoing messages automatically before they're sent
to the server.  Only one interceptor is allowed at a time within a Minecraft
instance.

*Args:*

- `interceptor`: callable that repeatedly accepts a string representing chat messages

Since: v2.1

See also:
  [`register_chat_message_listener()`](#register_chat_message_listener) for non-destructive listening of chat messages


#### unregister_chat_message_interceptor
*Usage:* <code>unregister_chat_message_interceptor()</code>

Unregisters the chat message interceptor, if one is currently registered.

*Returns:*

- `True` if successfully unregistered an interceptor.

Since: v2.1


#### screen_name
*Usage:* <code>screen_name()</code>

Gets the current GUI screen name, if there is one.

*Returns:*

- Name of current screen (str) or `None` if no GUI screen is being displayed.

Since: v3.2


#### BlockPos
Tuple representing `(x: int, y: int, z: int)` position in block space.

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

*Raises:*

  `BlockPackException` if blockpack cannot be read


#### BlockPack.read_file
*Usage:* <code>@classmethod BlockPack.read_file(filename: str, \*, relative_to_cwd=False) -> [BlockPack](#blockpack)</code>

Reads a blockpack from a file.

*Args:*

- `filename`: name of file relative to minescript/blockpacks dir unless it's an absolute path
    (".zip" is automatically appended to filename if it does not end with that extension)
- `relative_to_cwd`: if `True`, relative filename is taken to be relative to Minecraft dir

*Returns:*

- a new BlockPack containing blocks read from the file

*Raises:*

  `BlockPackException` if blockpack cannot be read


#### BlockPack.import_data
*Usage:* <code>@classmethod BlockPack.import_data(base64_data: str) -> [BlockPack](#blockpack)</code>

Creates a blockpack from base64-encoded serialized blockpack data.

*Args:*

- `base64_data`: base64-encoded string containing serialization of blockpack data.

*Returns:*

- a new BlockPack containing blocks read from the base64-encoded data

*Raises:*

  `BlockPackException` if blockpack cannot be read


#### BlockPack.block_bounds
*Usage:* <code>BlockPack.block_bounds() -> (BlockPos, BlockPos)</code>

Returns min and max bounding coordinates of blocks in this BlockPack.

*Raises:*

  `BlockPackException` if blockpack cannot be accessed


#### BlockPack.comments
*Usage:* <code>BlockPack.comments() -> Dict[str, str]</code>

Returns comments stored in this BlockPack.

*Raises:*

  `BlockPackException` if blockpack cannot be accessed

*Raises:*

  `BlockPackException` if blockpack operation fails


#### BlockPack.write_world
*Usage:* <code>BlockPack.write_world(\*, rotation: [Rotation](#rotation) = None, offset: [BlockPos](#blockpos) = None)</code>

Writes blocks from this BlockPack into the current world. Requires setblock, fill commands.

*Args:*

- `rotation`: rotation matrix to apply to block coordinates before writing to world
- `offset`: offset to apply to block coordiantes (applied after rotation)

*Raises:*

  `BlockPackException` if blockpack operation fails


#### BlockPack.write_file
*Usage:* <code>BlockPack.write_file(filename: str, \*, relative_to_cwd=False)</code>

Writes this BlockPack to a file.

*Args:*

- `filename`: name of file relative to minescript/blockpacks dir unless it's an absolute path
    (".zip" is automatically appended to filename if it does not end with that extension)
- `relative_to_cwd`: if `True`, relative filename is taken to be relative to Minecraft dir

*Raises:*

  `BlockPackException` if blockpack operation fails


#### BlockPack.export_data
*Usage:* <code>BlockPack.export_data() -> str</code>

Serializes this BlockPack into a base64-encoded string.

*Returns:*

- a base64-encoded string containing this blockpack's data

*Raises:*

  `BlockPackException` if blockpack operation fails


#### BlockPack.\_\_del\_\_
*Usage:* <code>del blockpack</code>

Frees this BlockPack to be garbage collected.

*Raises:*

  `BlockPackException` if blockpack operation fails


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

*Raises:*

  `BlockPackerException` if blockpacker operation fails


#### BlockPacker.pack
*Usage:* <code>BlockPacker.pack(\*, comments: Dict[str, str] = {}) -> [BlockPack](#blockpack)</code>

Packs blocks within this BlockPacker into a new BlockPack.

*Args:*

- `comments`: key, value pairs to include in the new BlockPack

*Returns:*

- a new BlockPack containing a snapshot of blocks from this BlockPacker

*Raises:*

  `BlockPackerException` if blockpacker operation fails


#### BlockPacker.\_\_del\_\_
*Usage:* <code>del blockpacker</code>

Frees this BlockPacker to be garbage collected.

*Raises:*

  `BlockPackerException` if blockpacker operation fails


