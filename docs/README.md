## Minescript v5.0 docs

Table of contents:

- [In-game commands](#in-game-commands)
    - [Command basics](#command-basics)
    - [Built-in commands](#built-in-commands)
- [Configuration](#configuration)
- [Pyjinn](#pyjinn)
- [Python API](#python-api)
    - [Script input](#script-input)
    - [Script output](#script-output)
    - [Script functions](#script-functions)
    - [Async script functions](#async-script-functions)
    - [java module](#java-module)
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
*Usage:* `\jobs [all]`

Lists the currently running Minescript jobs.

`\jobs` with no arguments list all jobs that are not managed by a parent job.

`\jobs all` lists all jobs, including child jobs, e.g. Pyjinn scripts embedded within a parent
Python job. See: [Pyjinn: Embedding Pyjinn in Python scripts](pyjinn.md#embedding-pyjinn-in-python-scripts)

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

- `autorun[WORLD NAME]` - command to run when entering a world named `WORLD NAME` (since v3.1)

    - The special name `*` indicates that the command should be run when entering
      all worlds, e.g. `autorun[*]=eval 'echo(f"Hello, {world_info().name}!")'`
      that welcomes you with message when connecting to a world.
    - Multiple `autorun[...]` config lines can be specified for the same world, or
      for `*`, in which case all matching commands are run concurrently.
    - A single `autorun[...]` config line can execute multiple commands in
      sequence by separating commands with a semicolon (`;`), e.g. the following would
      first print info about the world followed by the names of the 10 nearest entities:
      ```
      autorun[*]=eval "world_info()"; eval "[e.name for e in entities(sort='nearest', limit=10)]"
      ```
- `python` - file location of the Python interpreter (default for Windows is
  `"%userprofile%\AppData\Local\Microsoft\WindowsApps\python3.exe"`, and
  `"/usr/bin/python3"` for other operating systems)
- `command` - configuration for customizing invocations of scripts or executables from Minecraft
  commands. `command` can be specified multiple times for different filename extensions. For
  example, to execute `jar` files such as `foo.jar` from the Minescript command `\foo`:
  ```
  command = {
    "extension": ".jar",
    "command": [ "/usr/bin/java", "-jar", "{command}", "{args}" ],
    "environment": [ "FIRST_ENV_VAR=1234", "SECOND_ENV_VAR=2468" ]
  }
  ```
  `environment` is optional, allowing environment variables to be passed to scripts/executables.
  When configuring execution of Python scripts, remember to set `PYTHON_PATH` in `environment`.
- `command_path` - sets the command path for executing scripts/executables from Minescript commands.
  Entries that aren't absolute paths are relative to the `minescript` directory. Paths on Windows
  are separated by `;`, whereas paths on other operating systems are separated by `:`. The default
  is equivalent to the `minescript` directory and `system/exec` within it.
- `escape_command_double_quotes` - if true, escape double quotes that appear in `{args}` in the
  `command` field of a `command` config entry. Defaults to true for Windows, false for other
  operating systems.
- `max_commands_per_cycle` - number of Minescript-generated Minecraft commands to run per Minescript
  processing cycle. The higher the number, the faster the script will run.  Default is 15.
  (***Note:*** *Setting this value too high will make Minecraft less responsive and possibly
  crash.*)
- `command_cycle_deadline_usecs` - threshold in microseconds beyond which Minescript stops executing
  commands for the given execution cycle. Default is 10000 (10 milliseconds). A command that runs
  over the threshold continues to run to completion, but no more commands will be executed in that
  cycle.
- `ticks_per_cycle` - number of Minecraft game ticks to wait per Minecraft processing cycle. The
  lower the number, down to a minimum of 1, the faster the script will run.  Default is 1 since
  v3.2. (Previously, default was 3.)
- `incremental_command_suggestions` - enables or disables printing of incremental command
  suggestions to the in-game chat as the user types a Minescript command.  Default is false.
- `report_job_success_threshold_millis` - report on-screen that a script job has exited successfully
  if it has run for more than this duration in milliseconds; default value is 3000 (3 seconds); 0
  always reports; -1 never reports; exits of failed script jobs are always reported (since v4.0)
- `debug_output` - if true, enable debug output to `logs/latest.log`. Default is false.
- `minescript_on_chat_received_event` - if true, Minescript executes chat messages that start with
  `"You whisper to ..."` that contain a message starting with a backslash (`\`), e.g. from a command
  block executing `[execute as maxuser run tell maxuser \eval 1+2]`. Default is false.
- `secondary_enter_key_code` - The `enter` key (key code 257, called `return` on Macs) is the
  primary key for terminating commands in the chat. `secondary_enter_key_code` is a customizable
  secondary key which can also terminate commands. Default is 335 (`KEY_KP_ENTER`). See [GLFW
  Keyboard key tokens](https://www.glfw.org/docs/3.3/group__keys.html) for a list of key codes.
- `stderr_chat_ignore_pattern` - regular expression for ignoring lines of output from stderr of
  scripts. Default is the empty string: `"^$"`. This can be useful for Python installations that
  have spammy stderr output when running from Minescript.

## Pyjinn

**Pyjinn** is Minescript's integrated interpreter that's based on Python syntax.
See [Pyjinn docs](pyjinn.md) for details.


## Python API

### Script input

Parameters can be passed from Minecraft as input to a Python script as `sys.argv`.
For example, this Python script prints the result of rolling dice to the chat,
visible only to the local user
(copy code to `minecraft/minescript/roll_dice.py`):

```
import random
import sys

def print_dice_roll(num_dice: int, num_sides: int):
    results = []
    for _ in range(num_dice):
        results.append(random.randint(1, num_sides))
    die_or_dice = "die" if num_dice == 1 else "dice"
    print(f"Rolled {num_dice} {num_sides}-sided {die_or_dice}: {results}")

# Read arguments from `sys.argv` list.
# First arg for the number of dice is required.
# Second arg for the number of sides on the dice is optional,
# defaulting to 6.
num_dice = int(sys.argv[1])
num_sides = int(sys.argv[2]) if len(sys.argv) > 2 else 6
print_dice_roll(num_dice, num_sides)
```

The above script can be run from the Minecraft in-game chat to
output the result of rolling two 6-sided dice:

```
\roll_dice 2 6
```

That command passes arguments that set `num_dice` to `2` and `num_sides` to `6`.

### Script output

Minescript Python scripts can process outputs using `sys.stdout` and
`sys.stderr` (e.g. using Python's built-in `print` function),
or using functions defined in `minescript.py`:
[`echo`](#echo), [`echo_json`](#echo_json), [`chat`](#chat), and [`log`](#log).

```
# Prints a plain-text message to the in-game chat that's
# visible only to you (displayed as white text):
print("Note to self...")

# Same as the previous print(...) statement, except
# that the output to stdout is explicit:
print("Note to self...", file=sys.stdout)

# Same output as the previous print(...) statements,
# but using a Minescript function (behavior can be
# different from above print(...) statements when
# using "Script output redirection"; see below):
import minescript
minescript.echo("Note to self...")

# Echo JSON-formatted text (for styled and colored text)
# as a JSON string to the in-game chat only to yourself:
minescript.echo_json('{"text":"hello!", "color":"green"}')

# Or as a Python dict or list converted to JSON:
minescript.echo_json({"text":"hello!", "color":"green"})

# Prints a plain-text message to the in-game chat that's
# visible only to you, displayed as yellow text so that
# it's visually distinguishable from text written to stdout:
print("Note to self...", file=sys.stderr)

# Send a chat message that's visible to all players
# in the world:
minescript.chat("hi, friends!")

# Log a message to Minecrafts log file
# (in minecraft/logs/latest.log):
minescript.log("This is a debug message that does not appear in-game.")
```

#### Script output redirection

By default, output to a script's stdout (typically as `print("...")`)
and stderr (e.g. `print("...", file=sys.stderr)`) appears as plain
white (stdout) or yellow (stderr) text, visible only to you. But this
behavior can be overridden for a script job by using redirection operators
at the end of the Minescript command that's entered into the chat.

Here's an example script which can be copied to
`minecraft/minescript/output_example.py`:

```
import sys
print("Output to stdout")
print("Output to stderr", file=sys.stderr)
```

The script can be run from the in-game chat as:

```
\output_example
```

The text `Output to stdout` should appear in white in the chat, and
the text `Output to stderr` should appear in yellow, both visible only
to you.

But if you add `> chat` to the command like this:

```
\output_example > chat
```

then the output to stdout is redirected to the multiple-player chat.

Similarly, output can be redirected to the Minecraft log file
(`minecraft/logs/latest.log`) so that it doesn't appear in the in-game
chat, not even for you:

```
\output_example > log
```

Output to stdout can be disabled entirely for a script job by redirecting
with `> null`:

```
\output_example > null
```

Output to stderr can similarly be redirected with `2>`:

```
\output_example 2> chat
\output_example 2> log
\output_example 2> null
```

Both stdout and stderr can be redirected separately for the same script job,
e.g. redirect stdout to the multiple-player chat and stderr to the Minecraft
log file without displaying it in the chat, not even to yourself:

```
\output_example > chat 2> log
```

The space after `>` is optional:

```
\output_example >chat 2>log
```


### Script functions

Script functions imported from  [`minescript.py`](#minescript-module) can be called as functions
or [asynchronously](#async-script-functions).

When called directly, e.g. `minescript.screenshot("my_screenshot.png")`, script functions are
implemented in Java and typically return after the function has finished executing in Java. (A
handful of script functions return immediately while Java processing continues in the background:
[`execute`](#execute), [`echo`](#echo), [`echo_json`](#echo_json), [`chat`](#chat), and
[`log`](#log).)

There are 3 Java executors on which script functions can run:

1. `minescript.tick_loop`: the game tick loop which runs once even game tick (20 times per second,
which is a cycle time of 50 milliseconds)
1. `minescript.render_loop`: executed when a frame is rendered (typically around 60 frames per
second, which is a cycle time of 15-20 milliseconds, but can vary significantly based on game
performance)
1. `minescript.script_loop`: executor that processes script functions as quickly as possible, but
not on the rendering thread, so may lead to instability or even crash the game due to lack of
thread-safety in Minecraft Java code (cycle time is typically less than 1 millisecond)

The Java executor can be selected for a specific script function or within a specific script
context.  The selection of executor for a script function is determined by the following priority,
from highest to lowest:

1. Required executor for this function, if there is one. This is set with the function's
`set_required_executor` method, e.g.
`minescript.player.set_required_executor(minescript.script_loop)`. No required executor is set by
default.
1. Executor set by the script context using a `with` block, e.g.
   ```
   with minescript.script_loop:
     position = minescript.player().position  # processed by the high-speed script loop
     ...
   ```
1. Default executor for this function, if there is one. This is set with the function's
`set_default_executor` method, e.g.
`minescript.player.set_default_executor(minescript.script_loop)`. No default executor is set for
individual script functions by default.
1. Default executor for the current script job. This is set with the global function
[`set_default_executor`](#set_default_executor), e.g.
`minescript.set_default_executor(minescript.render_loop)`. Default value is `render_loop`.

Setting an executor for a script function affects only the calls of that function within that script
job. Concurrently running script jobs can set different executors for the same script function.

_**Note:** Script tasks using `<script_function>.as_task()`, `run_tasks()`, `schedule_tick_tasks()`,
and `schedule_render_tasks()` have been removed in Minescript 5.0 because their functionality is
redundant with and less expressive than embedded Pyjinn scripts. For embedding Pyjinn scripts within
Python scripts see: [Pyjinn: Embedding Pyjinn in Python scripts](pyjinn.md#embedding-pyjinn-in-python-scripts)_


### Async script functions

Async script functions allow scripts to run functions in the background and wait on them to
complete. This is useful for script functions that can take a long time to complete, e.g. several
seconds.

In this example, [`await_loaded_region`](#await_loaded_region) is used to block script execution
until a range of chunks has finished loading. This example uses a directly called script function
that executes synchronously (i.e. it doesn't return until the operation is complete):

```
import minescript

x, y, z = [int(p) for p in minescript.player().position]

# Waits until all chunks from (x ± 50, z ± 50) are loaded:
minescript.await_loaded_region(x - 50, z - 50, x + 50, z + 50)

minescript.echo("Chunks around player finished loading.")
```

This is an alternate version of the previous example, modified to use an async script function by
calling `.as_async(...)` instead of a direct call of the script function:

```
import minescript

x, y, z = [int(p) for p in minescript.player().position]

# .as_async(...) causes the script function to return a "future" value:
future = minescript.await_loaded_region.as_async(x - 50, z - 50, x + 50, z + 50)

# Do other work while the chunks are loading in the background...
minescript.echo("Waiting for chunks around player to finish loading...")

# Wait for future to complete, i.e. wait for chunks to finish loading:
future.wait()
minescript.echo("Chunks around player finished loading.")
```

A future value returned from an async script function can be waited on with a timeout which raises
`TimeoutError` if the timeout expires before the operation is able to complete. In this example,
the message `"Still waiting for chunks around player to finish loading..."` is repeatedly echoed
to the player's chat every 10 seconds until the chunks in the given range have finished loading:

```
import minescript

x, y, z = [int(p) for p in minescript.player().position]
while True:
  try:
    # Wait with a 10-second timeout:
    minescript.await_loaded_region.as_async(x - 50, z - 50, x + 50, z + 50).wait(timeout=10)
    minescript.echo("Chunks around player finished loading.")
    break
  except TimeoutError:
    minescript.echo("Still waiting for chunks around player to finish loading...")
```


### java module

Library for using Java reflection from Python, wrapping
the low-level Java API script functions (`java_*`).


*Example:*

```
from minescript import echo
from java import JavaClass

Minecraft = JavaClass("net.minecraft.client.Minecraft")
minecraft = Minecraft.getInstance()
echo("fps:", minecraft.getFps())
```

- [`AutoReleasePool`](#autoreleasepool)
- [`callAsyncScriptFunction`](#callasyncscriptfunction)
- [`callScriptFunction`](#callscriptfunction)
- [`eval_pyjinn_script`](#eval_pyjinn_script)
- [`Float`](#float)
- [`import_pyjinn_script`](#import_pyjinn_script)
- [`JavaBoundMember`](#javaboundmember)
- [`JavaClassType`](#javaclasstype)
- [`JavaFloat`](#javafloat)
- [`JavaFuture`](#javafuture)
- [`JavaInt`](#javaint)
- [`JavaObject`](#javaobject)
- [`JavaRef`](#javaref)
- [`JavaString`](#javastring)

#### AutoReleasePool
Context manager for managing Java references in Python using `with` blocks.

*Example:*

```
import minescript
from java import *
with AutoReleasePool() as auto:
  Object_class = auto(java_class("java.lang.Object"))
  Object_hashCode = auto(java_member(Object_class, "hashCode"))
  hello_string = auto(java_string("hello"))
  hash_value  = auto(java_call_method(hello_string, Object_hashCode))
  minescript.echo(java_to_string(hash_value))
```

All the Java references captured with `auto()` in the example are released when
the `with` block above exits, automatically calling [`java_release()`](#java_release).


#### AutoReleasePool.\_\_call\_\_
*Usage:* <code>AutoReleasePool.\_\_call\_\_(ref: JavaHandle) -> JavaHandle</code>

Track `ref` for auto-release when this pool is deleted or goes out of scope.

*Returns:*

- `ref` for convenient wrapping of functions returning a JavaHandle.


#### Float
Wrapper class for mirroring Java `float` in Python.

Python `float` maps to Java `double`, and Python doesn't have a built-in single-precision float.

```
  value: float
```

#### JavaRef
Reference counter for Java objects referenced from Python.

#### JavaObject
Python representation of a Java object.

#### JavaObject.\_\_init\_\_
*Usage:* <code>JavaObject(target_id: JavaHandle, ref: [JavaRef](#javaref) = None, is_script: bool = False)</code>

Constructs a Python handle to a Java object given a `JavaHandle`. 

#### JavaObject.toString
*Usage:* <code>JavaObject.toString() -> str</code>

Returns a `str` representation of `this.toString()` from Java.

#### JavaObject.set_value
*Usage:* <code>JavaObject.set_value(value: Any)</code>

Sets this JavaObject to reference `value` instead.

`value` can be any of the following types:
- bool: converted to Java Boolean
- int: converted to Java Integer
- Float: converted to Java Float
- float: converted to Java Double
- str: converted to Java String
- JavaObject: this JavaObject will reference the same Java object as `value`


#### JavaObject.\_\_getattr\_\_
*Usage:* <code>JavaObject.\_\_getattr\_\_(name: str)</code>

Accesses the field or method named `name`.

*Args:*

- `name`: name of a field or method on this JavaObject's class

*Returns:*

- If `name` matches a field on this JavaObject's class, then return the
  value of that field as a Python primitive or new JavaObject. Otherwise
  return a [`JavaBoundMember`](#javaboundmember) equivalent to the Java expression
  `this::methodName`.


#### JavaObject.\_\_len\_\_
*Usage:* <code>JavaObject.\_\_len\_\_() -> int</code>

If this JavaObject represents a Java array, returns the length of the array.

Raises `TypeError` if this isn't an array.


#### JavaObject.\_\_bool\_\_
*Usage:* <code>JavaObject.\_\_bool\_\_() -> int</code>

Returns False if the Java reference is null.

#### JavaObject.\_\_getitem\_\_
*Usage:* <code>JavaObject.\_\_getitem\_\_(i: int)</code>

If this JavaObject represents a Java array, returns `array[i]`.

*Args:*

- `i`: index into array from which to get an element

*Returns:*

- `array[i]` as a Python primitive value or JavaObject.

*Raises:*

  `TypeError` if this isn't an array.


#### JavaBoundMember
Representation of a Java method reference in Python.

#### JavaBoundMember.\_\_init\_\_
*Usage:* <code>JavaBoundMember(target_class_id: JavaHandle, target, name: str, ref: [JavaRef](#javaref) = None, script: [JavaObject](#javaobject) = None)</code>

Member that's bound to a target object, representing a field or method.

*Args:*

- `target_class_id`: Java object ID of enclosing class for this member
- `target`: either Java object ID of the target through which this member is accessed
- `name`: name of this member
- `ref`: JavaRef to manage reference lifetime (optional)
- `script`: Pyjinn Script object for accessing and calling script functions (optional)


#### JavaBoundMember.\_\_call\_\_
*Usage:* <code>JavaBoundMember.\_\_call\_\_(\*args)</code>

Calls the bound method with the given `args`.

*Returns:*

- A Python primitive (bool, int, float, str) if applicable, otherwise a JavaObject.


#### JavaInt
JavaObject subclass for Java Integer.

#### JavaFloat
JavaObject subclass for Java Float.

#### JavaString
JavaObject subclass for Java String.

#### JavaClassType
JavaObject subclass for Java class objects.

#### JavaClassType.is_enum
*Usage:* <code>JavaClassType.is_enum()</code>

Returns `True` if this class represents a Java enum type.

#### JavaClassType.\_\_getattr\_\_
*Usage:* <code>JavaClassType.\_\_getattr\_\_(name)</code>

Accesses the static field or static method named `name` on this Java class.

*Args:*

- `name`: name of a static field or static method on this Java class.

*Returns:*

- If `name` matches a static field on this Java class, then return the value of that field as
  a new JavaObject. Otherwise return a [`JavaBoundMember`](#javaboundmember) equivalent to the Java expression
  `ThisClass::staticMethodName`.


#### JavaClassType.\_\_call\_\_
*Usage:* <code>JavaClassType.\_\_call\_\_(\*args)</code>

Calls the constructor for this Java class that takes the given `args`, if applicable.

*Returns:*

- JavaObject representing the newly constructed Java object.


#### callScriptFunction
*Usage:* <code>callScriptFunction(func_name: str, \*args) -> [JavaObject](#javaobject)</code>

Calls the given Minescript script function.

*Args:*

- `func_name`: name of a Minescript script function
- `args`: args to pass to the given script function

*Returns:*

- The return value of the given script function as a Python primitive type or JavaObject.


#### JavaFuture
Java value that will become available in the future when an async function completes.

#### JavaFuture.wait
*Usage:* <code>JavaFuture.wait(timeout=None)</code>

Waits for the async function to complete.

*Args:*

- `timeout`: if not `None`, timeout in seconds to wait on the async function to complete

*Returns:*

- Python primitive value or JavaObject returned from the async function upon completion.


#### callAsyncScriptFunction
*Usage:* <code>callAsyncScriptFunction(func_name: str, \*args) -> [JavaFuture](#javafuture)</code>

Calls the given Minescript script function asynchronously.

*Args:*

- `func_name`: name of a Minescript script function
- `args`: args to pass to the given script function

*Returns:*

- [`JavaFuture`](#javafuture) that will hold the return value of the async funcion when complete.


#### eval_pyjinn_script
*Usage:* <code>eval_pyjinn_script(script_code: str) -> [JavaObject](#javaobject)</code>

Creates a Pyjinn script given source code as a string.

See: [Embedding Pyjinn in Python scripts](pyjinn.md#embedding-pyjinn-in-python-scripts)

*Args:*

- `script_code`: Pyjinn source code

*Returns:*

- new Java object of type `org.pyjinn.interpreter.Script`

Since: v5.0


#### import_pyjinn_script
*Usage:* <code>import_pyjinn_script(pyj_filename: str)</code>

Imports a Pyjinn script from a `.pyj` file.

See: [Embedding Pyjinn in Python scripts](pyjinn.md#embedding-pyjinn-in-python-scripts)

*Args:*

- `pyj_filename`: name a of `.pyj` file containing Pyjinn code to import

*Returns:*

- new Java object of type `org.pyjinn.interpreter.Script`

Since: v5.0


### minescript module

*Usage:* `import minescript  # from Python script`

This module contains APIs for scripts to call into the Minescript mod. It should be imported by
scripts as a library and not run directly.

APIs in this module are compatible with both Python and Pyjinn scripts unless `"Compatibility:"`
is specified.

- [`add_event_listener`](#add_event_listener)
- [`AddEntityEvent`](#addentityevent)
- [`append_chat_history`](#append_chat_history)
- [`await_loaded_region`](#await_loaded_region)
- [`BlockPack`](#blockpack)
- [`BlockPacker`](#blockpacker)
- [`BlockPackerException`](#blockpackerexception)
- [`BlockPos`](#blockpos)
- [`BlockRegion`](#blockregion)
- [`BlockUpdateEvent`](#blockupdateevent)
- [`chat`](#chat)
- [`chat_input`](#chat_input)
- [`ChatEvent`](#chatevent)
- [`ChatEventListener`](#chateventlistener)
- [`ChunkEvent`](#chunkevent)
- [`combine_rotations`](#combine_rotations)
- [`container_get_items`](#container_get_items)
- [`DamageEvent`](#damageevent)
- [`echo`](#echo)
- [`echo_json`](#echo_json)
- [`entities`](#entities)
- [`EntityData`](#entitydata)
- [`EventQueue`](#eventqueue)
- [`execute`](#execute)
- [`ExplosionEvent`](#explosionevent)
- [`flush`](#flush)
- [`get_block`](#get_block)
- [`get_block_list`](#get_block_list)
- [`get_block_region`](#get_block_region)
- [`getblock`](#getblock)
- [`getblocklist`](#getblocklist)
- [`HandItems`](#handitems)
- [`ItemStack`](#itemstack)
- [`java_access_field`](#java_access_field)
- [`java_array_index`](#java_array_index)
- [`java_array_length`](#java_array_length)
- [`java_assign`](#java_assign)
- [`java_bool`](#java_bool)
- [`java_call_method`](#java_call_method)
- [`java_call_script_function`](#java_call_script_function)
- [`java_class`](#java_class)
- [`java_ctor`](#java_ctor)
- [`java_double`](#java_double)
- [`java_field_names`](#java_field_names)
- [`java_float`](#java_float)
- [`java_int`](#java_int)
- [`java_long`](#java_long)
- [`java_member`](#java_member)
- [`java_method_names`](#java_method_names)
- [`java_new_array`](#java_new_array)
- [`java_new_instance`](#java_new_instance)
- [`java_release`](#java_release)
- [`java_string`](#java_string)
- [`java_to_string`](#java_to_string)
- [`job_info`](#job_info)
- [`JobInfo`](#jobinfo)
- [`KeyEvent`](#keyevent)
- [`KeyEventListener`](#keyeventlistener)
- [`log`](#log)
- [`ManagedCallback`](#managedcallback)
- [`MinescriptRuntimeOptions`](#minescriptruntimeoptions)
- [`MouseEvent`](#mouseevent)
- [`player`](#player)
- [`player_get_targeted_block`](#player_get_targeted_block)
- [`player_get_targeted_entity`](#player_get_targeted_entity)
- [`player_hand_items`](#player_hand_items)
- [`player_health`](#player_health)
- [`player_inventory`](#player_inventory)
- [`player_inventory_select_slot`](#player_inventory_select_slot)
- [`player_inventory_slot_to_hotbar`](#player_inventory_slot_to_hotbar)
- [`player_look_at`](#player_look_at)
- [`player_name`](#player_name)
- [`player_orientation`](#player_orientation)
- [`player_position`](#player_position)
- [`player_press_attack`](#player_press_attack)
- [`player_press_backward`](#player_press_backward)
- [`player_press_drop`](#player_press_drop)
- [`player_press_forward`](#player_press_forward)
- [`player_press_jump`](#player_press_jump)
- [`player_press_left`](#player_press_left)
- [`player_press_pick_item`](#player_press_pick_item)
- [`player_press_right`](#player_press_right)
- [`player_press_sneak`](#player_press_sneak)
- [`player_press_sprint`](#player_press_sprint)
- [`player_press_swap_hands`](#player_press_swap_hands)
- [`player_press_use`](#player_press_use)
- [`player_set_orientation`](#player_set_orientation)
- [`players`](#players)
- [`press_key_bind`](#press_key_bind)
- [`remove_event_listener`](#remove_event_listener)
- [`RenderEvent`](#renderevent)
- [`Rotation`](#rotation)
- [`Rotations`](#rotations)
- [`screen_name`](#screen_name)
- [`screenshot`](#screenshot)
- [`set_chat_input`](#set_chat_input)
- [`set_default_executor`](#set_default_executor)
- [`set_interval`](#set_interval)
- [`set_timeout`](#set_timeout)
- [`show_chat_screen`](#show_chat_screen)
- [`TakeItemEvent`](#takeitemevent)
- [`TargetedBlock`](#targetedblock)
- [`TickEvent`](#tickevent)
- [`Vector3f`](#vector3f)
- [`version_info`](#version_info)
- [`VersionInfo`](#versioninfo)
- [`world_info`](#world_info)
- [`WorldEvent`](#worldevent)
- [`WorldInfo`](#worldinfo)

#### BlockPos
Tuple representing `(x: int, y: int, z: int)` position in block space.

#### Vector3f
Tuple representing `(x: float, y: float, z: float)` position or offset in 3D space.

#### MinescriptRuntimeOptions
Minscript module options.

Compatibility: Python only.

```
  legacy_dict_return_values: bool = False  # set to `True` to emulate behavior before v4.0
```

#### execute
*Usage:* <code>execute(command: str)</code>

Executes the given command.

If `command` is prefixed by a backslash, it's treated as Minescript command,
otherwise it's treated as a Minecraft command (the slash prefix is optional).

*Note: This was named `exec` in Minescript 2.0. The old name is no longer
available in v3.0.*

Since: v2.1


#### echo
*Usage:* <code>echo(\*messages)</code>

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
*Usage:* <code>chat(\*messages)</code>

Sends messages to the chat.

If `messages[0]` is a str starting with a slash or backslash, automatically
prepends a space so that the messages are sent as a chat and not executed as
a command. If `len(messages)` is greater than 1, join messages with a space
separating them.  Ignores empty `messages`.

Update in v4.0:
  Support multiple messages.

Since: v2.0


#### log
*Usage:* <code>log(\*messages)</code>

Sends messages to latest.log.

Update in v4.0:
  Support multiple messages of any type. Auto-convert messages to `str`.

Since: v3.0


#### screenshot
*Usage:* <code>screenshot(filename: str=None)</code>

Takes a screenshot, similar to pressing the F2 key.

*Args:*

- `filename`: if specified, screenshot filename relative to the screenshots directory; ".png"
    extension is added to the screenshot file if it doesn't already have a png extension.

Since: v2.1


#### JobInfo

```
  job_id: int
  command: List[str]
  source: str
  status: str
  parent_job_id: Union[int, None]
  self: bool
```

#### job_info
*Usage:* <code>job_info() -> List[JobInfo]</code>

Return info about active Minescript jobs.

*Returns:*

- [`JobInfo`](#jobinfo).  For the enclosing job, `JobInfo.self` is `True`.

Since: v4.0


#### flush
*Usage:* <code>flush()</code>

Wait for all previously issued script commands from this job to complete.

Compatibility: Python only.

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
  Removed `done_callback` arg. Use [`player_position().as_async()`](#player_position) for async execution.


#### ItemStack

```
  item: str
  count: int
  nbt: str = None
  slot: int = None
  selected: bool = None
```

#### HandItems

```
  main_hand: ItemStack
  off_hand: ItemStack
```

#### player_hand_items
*Usage:* <code>player_hand_items() -> [HandItems](#handitems)</code>

Gets the items in the local player's hands.

*Returns:*

- Items in player's hands.
  (Legacy-style return value can be restored with `options.legacy_dict_return_values = True`)

Update in v4.0:
  Return [`HandItems`](#handitems) instead of `List[Dict[str, Any]]` by default.
  Removed `done_callback` arg. Use `player_hand_items.as_async()` for async execution.

Since: v2.0


#### player_inventory
*Usage:* <code>player_inventory() -> List[ItemStack]</code>

Gets the items in the local player's inventory.

*Returns:*

- Items in player's inventory.
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


#### player_inventory_slot_to_hotbar
*Usage:* <code>player_inventory_slot_to_hotbar(slot: int) -> int</code>

Swaps an inventory item into the hotbar.

*Args:*

- `slot`: inventory slot (9 or higher) to swap into the hotbar

*Returns:*

- hotbar slot (0-8) into which the inventory item was swapped

Update in mc1.21.4:
  No longer supported because ServerboundPickItemPacket was removed in Minecraft 1.21.4.

Update in v4.0:
  Removed `done_callback` arg. Use `player_inventory_slot_to_hotbar.as_async(...)`
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
  Removed `done_callback` arg. Use `player_inventory_select_slot.as_async(...)` for async execution.

Since: v3.0


#### press_key_bind
*Usage:* <code>press_key_bind(key_mapping_name: str, pressed: bool)</code>

Presses/unpresses a mapped key binding.

Valid values of `key_mapping_name` include: "key.advancements", "key.attack", "key.back",
"key.chat", "key.command", "key.drop", "key.forward", "key.fullscreen", "key.hotbar.1",
"key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5", "key.hotbar.6", "key.hotbar.7",
"key.hotbar.8", "key.hotbar.9", "key.inventory", "key.jump", "key.left",
"key.loadToolbarActivator", "key.pickItem", "key.playerlist", "key.right",
"key.saveToolbarActivator", "key.screenshot", "key.smoothCamera", "key.sneak",
"key.socialInteractions", "key.spectatorOutlines", "key.sprint", "key.swapOffhand",
"key.togglePerspective", "key.use"

*Args:*

- `key_mapping_name`: name of key binding
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
*Usage:* <code>player_orientation() -> List[float]</code>

Gets the local player's orientation.

*Returns:*

- [yaw: float, pitch: float] as angles in degrees

Since: v2.1


#### player_set_orientation
*Usage:* <code>player_set_orientation(yaw: float, pitch: float) -> bool</code>

Sets the local player's orientation.

*Args:*

- `yaw`: degrees rotation of the local player's orientation around the y axis
- `pitch`: degrees rotation of the local player's orientation from the x-z plane

*Returns:*

- True if successful

Since: v2.1


#### TargetedBlock

```
  position: BlockPos
  distance: float
  side: str
  type: str
```

#### player_get_targeted_block
*Usage:* <code>player_get_targeted_block(max_distance: float = 20) -> Union[TargetedBlock, None]</code>

Gets info about the nearest block, if any, in the local player's crosshairs.

*Args:*

- `max_distance`: max distance from local player to look for blocks

*Returns:*

- [`TargetedBlock`](#targetedblock) for the block targeted by the player, or `None` if no block is targeted.

Update in v4.0:
  Return value changed from `list` to [`TargetedBlock`](#targetedblock).

Since: v3.0


#### EntityData

```
  name: str
  type: str
  uuid: str
  id: int
  position: Vector3f
  yaw: float
  pitch: float
  velocity: Vector3f
  lerp_position: Vector3f = None
  health: float = None
  local: bool = None  # `True` if this the local player
  passengers: List[str] = None  # UUIDs of passengers as strings
  nbt: str = None
```

#### player_get_targeted_entity
*Usage:* <code>player_get_targeted_entity(max_distance: float = 20, nbt: bool = False) -> Union[EntityData, None]</code>

Gets the entity targeted in the local player's crosshairs, if any.

*Args:*

- `max_distance`: maximum distance to check for targeted entities
- `nbt`: if `True`, populate an `"nbt"` attribute for the player

*Returns:*

- [`EntityData`](#entitydata) for the entity targeted by the player, or `None` if no entity is targeted.
  (Legacy-style returned dict can be restored with `options.legacy_dict_return_values = True`)

Since: v4.0


#### player_health
*Usage:* <code>player_health() -> float</code>

Gets the local player's health.

Since: v3.1


#### player
*Usage:* <code>player(\*, nbt: bool = False) -> [EntityData](#entitydata)</code>

Gets attributes for the local player.

*Args:*

- `nbt`: if `True`, populate the `nbt` field for the player

*Returns:*

- [`EntityData`](#entitydata) representing a snapshot of values for the local player.
  (Legacy-style returned dict can be restored with `options.legacy_dict_return_values = True`)

Since: v4.0


#### players
*Usage:* <code>players(\*, nbt: bool = False, uuid: str = None, name: str = None, position: [Vector3f](#vector3f) = None, offset: [Vector3f](#vector3f) = None, min_distance: float = None, max_distance: float = None, sort: str = None, limit: int = None) -> List[EntityData]</code>

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
*Usage:* <code>entities(\*, nbt: bool = False, uuid: str = None, name: str = None, type: str = None, position: [Vector3f](#vector3f) = None, offset: [Vector3f](#vector3f) = None, min_distance: float = None, max_distance: float = None, sort: str = None, limit: int = None) -> List[EntityData]</code>

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


#### VersionInfo

```
  minecraft: str
  minescript: str
  mod_loader: str
  launcher: str
  os_name: str
  os_version: str
  minecraft_class_name: str
  pyjinn: str
```

#### version_info
*Usage:* <code>version_info() -> [VersionInfo](#versioninfo)</code>

Gets version info for Minecraft, Minescript, mod loader, launcher, and OS.

`minecraft_class_name` is the runtime class name of the main Minecraft class which may be
obfuscated.

*Returns:*

- [`VersionInfo`](#versioninfo)

Since: v4.0


#### WorldInfo

```
  game_ticks: int
  day_ticks: int
  raining: bool
  thundering: bool
  spawn: BlockPos
  hardcore: bool
  difficulty: str
  name: str
  address: str
```

#### world_info
*Usage:* <code>world_info() -> [WorldInfo](#worldinfo)</code>

Gets world properties.

If the current world is a multiplayer world loaded from the server list, then
the returned `name` and `address` attributes are the values as they appear in
the server list; otherwise `name` is the name of the locally saved world and
`address` is `localhost`.

`day_ticks` are the ticks associated with the day-night cycle.

Renamed from `world_properties()` from v3.1.

*Returns:*

- [`WorldInfo`](#worldinfo)

Since: v4.0


#### getblock
*Usage:* <code>getblock(x: int, y: int, z: int) -> str</code>

Gets the type of block at position (x, y, z).

*Args:*

- `x, y, z`: position of block to get

*Returns:*

- block type at (x, y, z) as a string

Update in v5.0:
  Added alias [`get_block(...)`](#get_block).


#### get_block
Alias for [`getblock(...)`](#getblock).

Since: v5.0


#### getblocklist
*Usage:* <code>getblocklist(positions: List[List[int]]) -> List[str]</code>

Gets the types of block at the specified [x, y, z] positions.

*Args:*

  list of positions as lists of x, y, z int coordinates, e.g. [[0, 0, 0], [0, 0, 1]]

*Returns:*

- block types at given positions as list of strings

Update in v5.0:
  Added alias [`get_block_list(...)`](#get_block_list).

Update in v4.0:
  Removed `done_callback` arg. Use `getblocklist.as_async(...)` for async execution.

Since: v2.1


#### get_block_list
Alias for [`getblocklist(...)`](#getblocklist).

Since: v5.0


#### BlockRegion
Accessor for blocks within an axis-aligned bounding box.

See [`get_block_region(...)`](#get_block_region) for creating a [`BlockRegion`](#blockregion).

Since: v5.0


#### BlockRegion.\_\_init\_\_
*Usage:* <code>BlockRegion(min_pos: [BlockPos](#blockpos), max_pos: [BlockPos](#blockpos), blocks: Tuple[str, ...])</code>

Creates a block region between `min_pos` and `max_pos`, inclusive.

*Args:*

- `min_pos`: minimum position of axis-aligned bounding box
- `max_pos`: maximum position of axis-aligned bounding box
- `blocks`: tuple of block type strings covering the volume of blocks between `min_pos` and
      `max_pos`, inclusive; given Lx, Ly, Lz lengths of the bounding box in x, y, and z
      dimensions, the first value in the tuple represents the block at the min_pos;
      the first Lx values represent the min y, z edge of the volume; the first Lx * Lz
      values represent the blocks in the min y plane of the volume; the last value represents
      the block at max_pos.


#### BlockRegion.get_block
*Usage:* <code>BlockRegion.get_block(x: int, y: int, z: int) -> str</code>

Gets the type of block at position (x, y, z).

#### BlockRegion.get_index
*Usage:* <code>BlockRegion.get_index(x: int, y: int, z: int) -> int</code>

Gets the index into `blocks` sequence for position (x, y, z).

#### get_block_region
*Usage:* <code>get_block_region(pos1: [BlockPos](#blockpos), pos2: [BlockPos](#blockpos), safety_limit: bool = True) -> [BlockRegion](#blockregion)</code>

Gets the types of blocks in the axis-aligned bounding box between pos1 and pos2, inclusive.

*Args:*

- `pos1, pos2`: opposing corners of an axis-aligned bounding box (aabb)
- `safety_limit`: if `True`, fail if requested volume spans more than 1600 chunks

*Returns:*

- [`BlockRegion`](#blockregion) covering the requested volume of blocks.

Since: v5.0


#### await_loaded_region
*Usage:* <code>await_loaded_region(x1: int, z1: int, x2: int, z2: int)</code>

Waits for chunks to load in the region from (x1, z1) to (x2, z2).

*Args:*

- `x1, z1, x2, z2`: bounds of the region for awaiting loaded chunks
- `timeout`: if specified, timeout in seconds to wait for the region to load

Compatibility: Python only.

Update in v4.0:
  Removed `done_callback` arg. Call now always blocks until region is loaded.


#### set_default_executor
*Usage:* <code>set_default_executor(executor: minescript_runtime.FunctionExecutor)</code>

Sets the default executor for script functions executed in the current script job.

Default value is `minescript.render_loop`.

*Args:*

- `executor`: one of `minescript.tick_loop`, `minescript.render_loop`, or `minescript.script_loop`

Compatibility: Python only.

Since: v4.0


#### KeyEvent
Key event data.

For a list of key codes, see: https://www.glfw.org/docs/3.4/group__keys.html
`action` is 0 for key up, 1 for key down, and 2 for key repeat.

```
  type: str
  time: float
  key: int
  scan_code: int
  action: int
  modifiers: int
  screen: str
```

#### MouseEvent
Mouse event data.

`action` is 0 for mouse up and 1 for mouse down.

```
  type: str
  time: float
  button: int
  action: int
  modifiers: int
  x: float
  y: float
  screen: str = None
```

#### ChatEvent

```
  type: str
  time: float
  message: str
```

#### AddEntityEvent

```
  type: str
  time: float
  entity: EntityData
```

#### BlockUpdateEvent

```
  type: str
  time: float
  position: BlockPos
  old_state: str
  new_state: str
```

#### TakeItemEvent

```
  type: str
  time: float
  player_uuid: str
  item: EntityData
  amount: int
```

#### DamageEvent

```
  type: str
  time: float
  entity_uuid: str
  cause_uuid: str
  source: str
```

#### ExplosionEvent

```
  type: str
  time: float
  position: Vector3f
  blockpack_base64: str
```

#### ChunkEvent

```
  type: str
  time: float
  loaded: bool
  x_min: int
  z_min: int
  x_max: int
  z_max: int
```

#### WorldEvent

```
  type: str
  time: float
  connected: bool
```

#### EventQueue
Queue for managing events.

Implements context management so that it can be used with a `with` expression
to automatically unregister event listeners at the end of the block, e.g.

```
with EventQueue() as event_queue:
  event_queue.register_chat_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.CHAT and "knock knock" in event.message.lower():
      echo("Who's there?")
```

Compatibility: Python only. (See [`add_event_listener`](#add_event_listener) for Pyjinn event handling.)

Since: v4.0


#### EventQueue.\_\_init\_\_
*Usage:* <code>EventQueue()</code>

Creates an event registration handler.

#### EventQueue.register_key_listener
*Usage:* <code>EventQueue.register_key_listener()</code>

Registers listener for `EventType.KEY` events as [`KeyEvent`](#keyevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_key_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.KEY:
      if event.action == 0:
        action = 'up'
      elif event.action == 1:
        action = 'down'
      else:
        action = 'repeat'
      echo(f"Got key {action} with code {event.key}")
```


#### EventQueue.register_mouse_listener
*Usage:* <code>EventQueue.register_mouse_listener()</code>

Registers listener for `EventType.MOUSE` events as [`MouseEvent`](#mouseevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_mouse_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.MOUSE:
      echo(f"Got mouse {'up' if event.action == 0 else 'down'} of button {event.button}")
```


#### EventQueue.register_chat_listener
*Usage:* <code>EventQueue.register_chat_listener()</code>

Registers listener for `EventType.CHAT` events as [`ChatEvent`](#chatevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_chat_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.CHAT:
      if not event.message.startswith("> "):
        echo(f"> Got chat message: {event.message}")
```


#### EventQueue.register_outgoing_chat_interceptor
*Usage:* <code>EventQueue.register_outgoing_chat_interceptor(\*, prefix: str = None, pattern: str = None)</code>

Registers listener for `EventType.OUTGOING_CHAT_INTERCEPT` events as [`ChatEvent`](#chatevent).

Intercepts outgoing chat messages from the local player. Interception can be restricted to
messages matching `prefix` or `pattern`. Intercepted messages can be chatted with [`chat()`](#chat).

`prefix` or `pattern` can be specified, but not both. If neither `prefix` nor
`pattern` is specified, all outgoing chat messages are intercepted.

*Args:*

- `prefix`: if specified, intercept only the messages starting with this literal prefix
- `pattern`: if specified, intercept only the messages matching this regular expression

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_outgoing_chat_interceptor(pattern=".*%p.*")
  while True:
    event = event_queue.get()
    if event.type == EventType.OUTGOING_CHAT_INTERCEPT:
      # Replace "%p" in outgoing chats with your current position.
      chat(event.message.replace("%p", str(player().position)))
```


#### EventQueue.register_add_entity_listener
*Usage:* <code>EventQueue.register_add_entity_listener()</code>

Registers listener for `EventType.ADD_ENTITY` events as [`AddEntityEvent`](#addentityevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_add_entity_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.ADD_ENTITY:
      echo(f"Entity added: {event.entity.name}")
```


#### EventQueue.register_block_update_listener
*Usage:* <code>EventQueue.register_block_update_listener()</code>

Registers listener for `EventType.BLOCK_UPDATE` events as [`BlockUpdateEvent`](#blockupdateevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_block_update_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.BLOCK_UPDATE:
      echo(f"Block updated at {event.position} to {event.new_state}")
```


#### EventQueue.register_take_item_listener
*Usage:* <code>EventQueue.register_take_item_listener()</code>

Registers listener for `EventType.TAKE_ITEM` events as [`TakeItemEvent`](#takeitemevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_take_item_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.TAKE_ITEM:
      echo(f"Item taken: {event.item.type}")
```


#### EventQueue.register_damage_listener
*Usage:* <code>EventQueue.register_damage_listener()</code>

Registers listener for `EventType.DAMAGE` events as [`DamageEvent`](#damageevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_damage_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.DAMAGE:
      echo(f"Damage from {event.source}")
```


#### EventQueue.register_explosion_listener
*Usage:* <code>EventQueue.register_explosion_listener()</code>

Registers listener for `EventType.EXPLOSION` events as [`ExplosionEvent`](#explosionevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_explosion_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.EXPLOSION:
      echo(f"Explosion at {event.position}")
```


#### EventQueue.register_chunk_listener
*Usage:* <code>EventQueue.register_chunk_listener()</code>

Registers listener for `EventType.CHUNK` events as [`ChunkEvent`](#chunkevent).

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_chunk_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.CHUNK:
      x = event.x_min
      z = event.z_min
      echo(f"Chunk {'loaded' if event.loaded else 'unloaded'} at {x}, {z}")
```


#### EventQueue.register_world_listener
*Usage:* <code>EventQueue.register_world_listener()</code>

Registers listener for `EventType.WORLD` events as [`WorldEvent`](#worldevent).

Script jobs are automatically terminated when the user's game client disconnects from a world
unless the script has an active "world" listener registered. All script jobs, including ones
with "world" listeners, are terminated when the game client exits.

*Example:*

```
with EventQueue() as event_queue:
  event_queue.register_world_listener()
  while True:
    event = event_queue.get()
    if event.type == EventType.WORLD:
      if event.connected:
        log(f"Connected to world {world_info().name}.")
      else:
        log("Disconnected from world.")
```

Since: v5.0


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

Deprecated listener for keyboard events. Use [`EventQueue.register_key_listener`](#eventqueueregister_key_listener) instead.

Compatibility: Python only.

Update in v4.0:
  Deprecated in favor of [`EventQueue.register_key_listener`](#eventqueueregister_key_listener).

Since: v3.2


#### ChatEventListener
*Usage:* <code>ChatEventListener()</code>

Deprecated listener for chat message events.

Use `EventQueue.register_chat_message_listener` instead.

Compatibility: Python only.

Update in v4.0:
  Deprecated in favor of `EventQueue.register_chat_message_listener`.

Since: v3.2


#### screen_name
*Usage:* <code>screen_name() -> Union[str, None]</code>

Gets the current GUI screen name, if there is one.

*Returns:*

- Name of current screen (str) or `None` if no GUI screen is being displayed.

Since: v3.2


#### show_chat_screen
*Usage:* <code>show_chat_screen(show: bool, prompt: str = None) -> bool</code>

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
*Usage:* <code>chat_input() -> List[Union[str, int]]</code>

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
 


#### BlockPack.visit_blocks
*Usage:* <code>BlockPack.visit_blocks(setblock: Callable[[int, int, int, str], None], fill: Callable[[int, int, int, int, int, int, str], None]) -> None</code>

Invokes the given callbacks to visit all the blocks in this BlockPack.

*Args:*

- `setblock`: for each block that's not adjacent to any blocks of the same type, invoke this as
      setblock(x, y, z, block)
- `fill`: for each axis-aligned bounding box (aabb) of blocks of the same type greater than 1x1x1
      between opposing corners (x1, y1, z1) and (x2, y2, z2), invoke this as
      fill(x1, y1, z1, x2, y2, z2, block)

Compatibility: Pyjinn only.


#### BlockPack.\_\_del\_\_
*Usage:* <code>del blockpack</code>

Frees this BlockPack to be garbage collected.

#### BlockPackerException
Exception thrown from failed [`BlockPack`](#blockpack) operations.

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

  [`BlockPackerException`](#blockpackerexception) if blockpacker operation fails


#### BlockPacker.fill
*Usage:* <code>BlockPacker.fill(pos1: [BlockPos](#blockpos), pos2: [BlockPos](#blockpos), block_type: str)</code>

Fills blocks within this BlockPacker.

*Args:*

- `pos1, pos2`: coordinates of opposing corners of a rectangular volume to fill
- `block_type`: block descriptor to fill

*Raises:*

  [`BlockPackerException`](#blockpackerexception) if blockpacker operation fails


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
*Usage:* <code>java_class(name: str) -> JavaHandle</code>

Looks up Java class by fully qualified name. Returns handle to the Java class object.

*Example:*
 [`java_class("net.minescript.common.Minescript")`](#java_class)

If running Minecraft with unobfuscated Java symbols:
[`java_class("net.minecraft.client.Minecraft")`](#java_class)

If running Minecraft with obfuscated symbols, `name` must be the fully qualified and obfuscated
class name.

Compatibility: Python only.

Since: v4.0


#### java_string
*Usage:* <code>java_string(s: str) -> JavaHandle</code>

Returns handle to a Java String.

Compatibility: Python only.

Since: v4.0


#### java_double
*Usage:* <code>java_double(d: float) -> JavaHandle</code>

Returns handle to a Java Double.

Compatibility: Python only.

Since: v4.0


#### java_float
*Usage:* <code>java_float(f: float) -> JavaHandle</code>

Returns handle to a Java Float.

Compatibility: Python only.

Since: v4.0


#### java_long
*Usage:* <code>java_long(l: int) -> JavaHandle</code>

Returns handle to a Java Long.

Compatibility: Python only.

Since: v4.0


#### java_int
*Usage:* <code>java_int(i: int) -> JavaHandle</code>

Returns handle to a Java Integer

Compatibility: Python only.

Since: v4.0


#### java_bool
*Usage:* <code>java_bool(b: bool) -> JavaHandle</code>

Returns handle to a Java Boolean.

Compatibility: Python only.

Since: v4.0


#### java_ctor
*Usage:* <code>java_ctor(clazz: JavaHandle)</code>

Returns handle to a constructor set for the given class handle.

*Args:*

- `clazz`: Java class handle returned from [`java_class`](#java_class)

Compatibility: Python only.

Since: v4.0


#### java_new_instance
*Usage:* <code>java_new_instance(ctor: JavaHandle, \*args: List[JavaHandle]) -> JavaHandle</code>

Creates new Java instance.

*Args:*

- `ctor`: constructor set returned from [`java_ctor`](#java_ctor)
- `args`: handles to Java objects to pass as constructor params

*Returns:*

- handle to newly created Java object.

Compatibility: Python only.

Since: v4.0


#### java_member
*Usage:* <code>java_member(clazz: JavaHandle, name: str) -> JavaHandle</code>

Gets Java member(s) matching `name`.

*Args:*

- `clazz`: Java class handle returned from [`java_class`](#java_class) to look up member within
- `name`: name of member to look up within `clazz`

*Returns:*

- Java member object for use with [`java_access_field`](#java_access_field) or [`java_call_method`](#java_call_method).

Compatibility: Python only.

Since: v4.0


#### java_access_field
*Usage:* <code>java_access_field(target: JavaHandle, field: JavaHandle) -> Union[JavaHandle, None]</code>

Accesses a field on a target Java object.

*Args:*

- `target`: Java object handle from which to access a field
- `field`: handle returned from [`java_member`](#java_member)

*Returns:*

- Handle to Java object returned from field access, or `None` if `null`.

Compatibility: Python only.

Since: v4.0


#### java_call_method
*Usage:* <code>java_call_method(target: JavaHandle, method: JavaHandle, \*args: List[JavaHandle]) -> Union[JavaHandle, None]</code>

Invokes a method on a target Java object.

*Args:*

- `target`: Java object handle on which to call a method
- `method`: handle returned from [`java_member`](#java_member)
- `args`: handles to Java objects to pass as method params

*Returns:*

- handle to Java object returned from method call, or `None` if `null`.

Compatibility: Python only.

Since: v4.0


#### java_call_script_function
*Usage:* <code>java_call_script_function(func_name: Union[str, JavaHandle], \*args: List[JavaHandle]) -> JavaHandle</code>

Calls the requested script function with Java params.

*Args:*

- `func_name`: name of the script function, as a Python str or a handle to a Java String
- `args`: handles to Java objects to pass as args to the script function

*Returns:*

- handle to Java object (`Optional<JsonElement>`) returned from the script function.

Compatibility: Python only.

Since: v4.0


#### java_array_length
*Usage:* <code>java_array_length(array: JavaHandle) -> int</code>

Returns length of Java array as Python integer.

Compatibility: Python only.

Since: v4.0


#### java_array_index
*Usage:* <code>java_array_index(array: JavaHandle, i: int) -> Union[JavaHandle, None]</code>

Gets indexed element of Java array handle.

*Args:*

- `array`: handle to Java array object
- `i`: index into array

*Returns:*

- handle to object at `array[i]` in Java, or `None` if `null`.

Compatibility: Python only.

Since: v4.0


#### java_new_array
*Usage:* <code>java_new_array(element_type: JavaHandle, \*elements: List[JavaHandle]) -> JavaHandle</code>

Creates a new Java array of the given element type with the given elements.

*Args:*

- `element_type`: handle to Java class (Class<?>) to use for the new array's type
- `elements`: handles to Java objects to populate the new array

*Returns:*

- handle to new Java array.

Compatibility: Python only.

Since: v5.0


#### java_to_string
*Usage:* <code>java_to_string(target: JavaHandle) -> str</code>

Returns Python string from calling `target.toString()` in Java.

Compatibility: Python only.

Since: v4.0


#### java_assign
*Usage:* <code>java_assign(dest: JavaHandle, source: JavaHandle)</code>

Reassigns `dest` to reference the object referenced by `source`.

Upon success, both `dest` and `source` reference the same Java object that was initially
referenced by `source`.

Compatibility: Python only.

Since: v4.0


#### java_field_names
*Usage:* <code>java_field_names(clazz: JavaHandle) -> List[str]</code>

Returns a list of fields names for the class referenced by handle `clazz`.

If mappings are installed, official field names are returned.

Compatibility: Python only.

Since: v5.0


#### java_method_names
*Usage:* <code>java_method_names(clazz: JavaHandle) -> List[str]</code>

Returns a list of methods names for the class referenced by handle `clazz`.

If mappings are installed, official method names are returned.

Compatibility: Python only.

Since: v5.0


#### java_release
*Usage:* <code>java_release(\*targets: List[JavaHandle])</code>

Releases Java reference(s) referred to by `targets`.

Compatibility: Python only.

Since: v4.0


#### add_event_listener
*Usage:* <code>add_event_listener(event_type: str, callback: Callable[[Any], None], \*\*args) -> int</code>

Adds an event listener with the given callback and args.

  Supported event types:
  
  - `"add_entity"` - [`AddEntityEvent`](#addentityevent)
  - `"block_update"` - [`BlockUpdateEvent`](#blockupdateevent)
  - `"chat"` - [`ChatEvent`](#chatevent)
  - `"chunk"` - [`ChunkEvent`](#chunkevent)
  - `"damage"` - [`DamageEvent`](#damageevent)
  - `"explosion"` - [`ExplosionEvent`](#explosionevent)
  - `"key"` - [`KeyEvent`](#keyevent)
  - `"mouse"` - [`MouseEvent`](#mouseevent)
  - `"outgoing_chat_intercept"` - [`ChatEvent`](#chatevent)
  - `"render"` - [`RenderEvent`](#renderevent)
  - `"take_item"` - [`TakeItemEvent`](#takeitemevent)
  - `"tick"` - [`TickEvent`](#tickevent)
  - `"world"` - [`WorldEvent`](#worldevent)

  Compatibility: Pyjinn only. (See [`EventQueue`](#eventqueue) for Python event handling.)
  

#### remove_event_listener
*Usage:* <code>remove_event_listener(listener_id: int) -> bool</code>

Removes an event listener previously added using [`add_event_listener()`](#add_event_listener).

  Compatibility: Pyjinn only.
  

#### RenderEvent
Render event for use with callback to [`add_event_listener()`](#add_event_listener).

  Compatibility: Pyjinn only.
  
```
  type: str  # "render"
  context: Any  # Render context provided by the mod loader.
  time: float
```

#### TickEvent
Tick event for use with callback to [`add_event_listener()`](#add_event_listener).

  Compatibility: Pyjinn only.
  
```
  type: str  # "tick"
  time: float
```

#### set_timeout
*Usage:* <code>set_timeout(callback: Callable[..., None], timer_millis: int, \*args, \*\*kwargs) -> int</code>

Schedules `callback` to be invoked once after `timer_millis` milliseconds.
  
  Returns:
    an integer ID for the callback which can be canceled with [`remove_event_listener()`](#remove_event_listener).

  Compatibility: Pyjinn only.
  

#### set_interval
*Usage:* <code>set_interval(callback: Callable[..., None], timer_millis: int, \*args, \*\*kwargs) -> int</code>

Schedules `callback` to be invoked every `timer_millis` milliseconds.
  
  Returns:
    an integer ID for the callback which can be canceled with [`remove_event_listener()`](#remove_event_listener).

  Compatibility: Pyjinn only.
  

#### ManagedCallback
Wrapper for managing callbacks passed to Java APIs.

  Example:

  ```
  callback = ManagedCallback(on_hud_render)
  HudRenderCallback.EVENT.register(HudRenderCallback(callback))

  # Cancel after 1 second (1000 milliseconds):
  set_timeout(callback.cancel, 1000)
  ```

  Compatibility: Pyjinn only.
  

#### ManagedCallback.\_\_init\_\_
*Usage:* <code>ManagedCallback(callback, cancel_on_exception=True, default_value=None)</code>

Creates a managed callback.

  Args:
- `  callback`: a callable function or object to manage
- `  cancel_on_exception`: if the callback raises an exception, cancel the callback
- `  default_value`: value to return immediately if callback is called after being canceled
  

#### ManagedCallback.cancel
*Usage:* <code>ManagedCallback.cancel()</code>

Cancels the callback, returning `default_value` if it continues to be called.

#### ManagedCallback.\_\_call\_\_
*Usage:* <code>ManagedCallback.\_\_call\_\_(\*args)</code>

Calls this callback, checking for cancellation.

