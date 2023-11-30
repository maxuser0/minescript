## Minescript v2.0 docs

Table of contents:

- [In-game commands](#in-game-commands)
    - [Command basics](#command-basics)
    - [General commands](#general-commands)
    - [Advanced commands](#advanced-commands)
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

### General commands

#### ls
*Usage:* `\ls`

List all available Minescript commands, including both Minescript built-in
commands and Python scripts in the **minescript** folder.

#### help
*Usage:* `\help  NAME`

Prints documentation for the given script or command name.

Since: v1.19.2

#### copy
*Usage:* `\copy  X1  Y1  Z1  X2  Y2  Z2  [LABEL]`

Copies blocks within the rectangular box from (X1, Y1, Z1) to (X2, Y2, Z2),
similar to the coordinates passed to the /fill command. LABEL is optional,
allowing a set of blocks to be named.

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

***Note:*** *Setting this value too low will make Minecraft less responsive and
possibly crash.*

Default is 3.

#### minescript_incremental_command_suggestions 
*Usage:* `\minescript_incremental_command_suggestions  BOOL`

Enables or disables printing of incremental command suggestions to the in-game
chat as the user types a Minescript command.

Default is false.

Since: v2.0 (in prior versions, incremental command suggestions were
unconditionally enabled)

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
[`echo`](#echo), [`chat`](#chat), and [`exec`](#exec)).  The
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

# Since Minescript v2.0 this can be written as:
minescript.exec("/setblock ~ ~-1 ~ yellow_concrete")
```

***Note:*** *`exec` has been renamed to `execute` in Minescript v2.1 to avoid
confusion with Python's built-in `exec`.*

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

From a Python script in the **minescript** folder, import the `minescript`
module:

```
import minescript
```

#### exec
*Usage:* `exec(command: str)`

Executes the given Minecraft or Minescript command.

If command doesn't already start with a slash or backslash, automatically
prepends a slash. Ignores leading and trailing whitespace, and ignores empty
commands.

*Note: This is named `execute` in Minescript 2.1. The old name is still
available in v2.1, but is deprecated and will be removed in a future version.*

Since: v2.0


#### echo
*Usage:* `echo(message: Any)`

Echoes message to the chat.

The echoed message is visible only to the local player.

Since: v2.0


#### chat
*Usage:* `chat(message: str)`

Sends the given message to the chat.

If message starts with a slash or backslash, automatically prepends a space
so that the message is sent as a chat and not executed as a command.  Ignores
empty messages.

Since: v2.0


#### player_position
*Usage:* `player_position(done_callback=None)`

Gets the local player’s position.

*Args:*

- `done_callback`: if given, return immediately and call
  done_callback(return_value) asynchronously when return_value is ready

*Returns:*

- if `done_callback` is None, returns player’s position as [x, y, z]

*Example:*
```
x, y, z = minescript.player_position()
```

#### player_hand_items
*Usage:* `player_hand_items(done_callback=None)`

Gets the items in the local player's hands.

*Args:*

- `done_callback`: if given, return immediately and call
  `done_callback(return_value)` asynchronously when return_value is ready.

*Returns:*

- If `done_callback` is `None`, returns items in player's inventory as list of
  items where each item is a dict: `{"item": str, "count": int, "nbt": str}`

Since: v2.0


#### player_inventory
*Usage:* `player_inventory(done_callback=None)`

Gets the items in the local player's inventory.

*Args:*

- `done_callback`: if given, return immediately and call
  `done_callback(return_value)` asynchronously when return_value is ready

*Returns:*

- If `done_callback` is `None`, returns items in player's inventory as list of
  items where each item is a dict: `{"item": str, "count": int, "nbt": str}`

Since: v2.0


#### getblock
*Usage:* `getblock(x: int, y: int, z: int, done_callback=None)`

Gets the type of block at position (x, y, z).

*Args:*

- `done_callback`: if given, return immediately and call
  `done_callback(return_value)` asynchronously when return_value is ready

*Returns:*

- if done_callback is None, returns the block type at (x, y, z) as a string

*Example:*

```
block_type = minescript.getblock(x, y, z)
```

#### await_loaded_region
*Usage:* `await_loaded_region(x1: int, z1: int, x2: int, z2: int, done_callback=None)`

Notifies the caller when all the chunks in the region from (x1, z1) to (x2, z2)
are fully loaded. This function is useful for making sure that a region is
fully loaded before setting or filling blocks within it.

*Args:*

- `done_callback`: if given, return immediately and call
  `done_callback(return_value)` asynchronously when return_value is ready

*Returns:*

- if done_callback is None, returns True when the requested region is fully
  loaded.

*Examples:*

[1] Don't do any work until the region is done loading (synchronous / blocking
call):

```
print("About to wait for region to load...", file=sys.stderr)

# Load all chunks within (x, z) bounds (0, 0) and (320, 160):
minescript.await_loaded_region(0, 0, 320, 160)

print("Region finished loading.", file=sys.stderr)
```

[2] Continue doing work on the main thread while the region loads in the
background (asynchronous / non-blocking call):

```
import minescript
import threading

lock = threading.Lock()

def on_region_loaded(loaded):
  if loaded:
    print("Region loaded ok.", file=sys.stderr)
  else:
    print("Region failed to load.", file=sys.stderr)
  lock.release()

# Acquire the lock, to be released later by on_region_loaded().
lock.acquire()

# Calls on_region_loaded(...) when region finishes 
# loading all chunks within (x, z) bounds (0, 0)
# and (320, 160):
minescript.await_loaded_region(
    0, 0, 320, 160, on_region_loaded)

print("Do other work while region loads...", file=sys.stderr)

print("Now wait for region to finish loading...", file=stderr)
lock.acquire()

print("Do more work now that region finished loading...", file=stderr)
```


#### register_chat_message_listener
*Usage:* `register_chat_message_listener(listener: Callable[[str], None])`

Registers a listener for receiving chat messages. One listener allowed per job.

Listener receives both incoming and outgoing chat messages.

*Args:*

- `listener`: callable that repeatedly accepts a string representing chat messages

Since: v2.0


#### unregister_chat_message_listener
*Usage:* `unregister_chat_message_listener()`

Unegisters a chat message listener, if any, for the currently running job.

*Returns:*

- `True` if successfully unregistered a listener, `False` otherwise.

Since: v2.0

