## `interpreter v1`

A Python REPL interpreter in the Minecraft chat with
Java reflection.

If a file is found in any of the directories of the config
variable `command_path` from `config.txt` with the filename
`.interpreter_init.py`, that script is loaded during startup
of the interpreter.

When the interpreter launches, the prompt ">>>" appears
in the Minecraft chat. Enter Python statements or expressions
just as you would in a Python REPL in a terminal.

Put the interpreter in the background by hitting the escape
key or deleting the ">>>" prompt to enter a Minecraft command,
chat message, or Minescript command.

Bring the interpreter to the foreground by pressing "i" while
no GUI screens are visible.

Exit the interpreter by typing "." at the ">>>" prompt.

&nbsp;

**Requirements**

  Minescript v4.0 or higher
  [lib_java](https://minescript.net/sdm_downloads/lib_java) v1 or higher

**Usage**
  `\interpreter`
