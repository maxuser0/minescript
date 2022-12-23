# Minescript

## Introduction

**Minescript** is a platform for controlling and interacting with Minecraft
using scripts written in the Python programming language. It is implemented as
mod that comes in two flavors: one for [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/) and one for [Fabric](https://fabricmc.net/).

The examples below require Minescript v2.0 or higher.

## How it works

Users place Python scripts (`.py` files) in the `minecraft/minescript` folder.
These scripts can be run from the Minecraft chat console with a leading
backslash and dropping the `.py`. E.g. a file at `minecraft/minescript/example.py`
could be executed from the Minecraft chat as:

```
\example
```

`minescript.py` is automatically installed in the `minecraft/minescript` folder
the first time that Minecraft launches with the Minescript mod installed. This
Python module contains a library of functions for accessing Minecraft
functionality:

```
# example.py:

import minescript

# Write a message to the chat that only you can see:
minescript.echo("Hello, world!")

# Write a chat message that other players can see:
minescript.chat("Hello, everyone!")

# Get your player's current position:
x, y, z = minescript.player_position()

# Set the block directly beneath your player:
x, y, z = int(x), int(y), int(z)
minescript.execute(f"setblock {x} {y-1} {z} yellow_concrete")

# Print the type of block at a particular location:
minescript.echo(minescript.getblock(x, y, z))

# Display the contents of your inventory:
for item in minescript.player_inventory():
  minescript.echo(item["item"])
```

## Pre-built mod jars

Pre-built mod jars for Fabric and Forge can be downloaded from
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/minescript/files).

## License

All code, compiled or in source form, in the built mod jar is licensed as GPL
v3 (specifically `SPDX-License-Identifier: GPL-3.0-only`). Sources within the
`tools` directory that are not distributed in the mod jar and are not required
for building or running the mod jar may be covered by a different license.
