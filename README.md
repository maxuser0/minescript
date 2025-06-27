# Minescript

## Introduction

**Minescript** is a platform for controlling and interacting with Minecraft using scripts written in
Python and other scripting languages. It is implemented as mod for [Fabric](https://fabricmc.net/),
[Forge](https://files.minecraftforge.net/net/minecraftforge/forge/), and
[NeoForge](https://neoforged.net/).

The examples below require Minescript 4.0 or higher.

## Terms of Service

Before using Minescript, please review the [Terms of Service](https://github.com/maxuser0/minescript/blob/main/TERMS_OF_SERVICE.md).

## How it works

Place Python scripts (`.py` files) in the `minescript` folder (located inside the `minecraft`
folder) to run them from the Minecraft chat console. A file at `minecraft/minescript/example.py`
can be executed from the Minecraft chat as:

```
\example
```

`minescript.py` is a script library that's automatically installed in the
`minecraft/minescript/system/lib` folder the first time running Minecraft with the Minescript mod
installed. `minescript.py` contains a library of functions for accessing Minecraft functionality:

```
# example.py:

import minescript

# Write a message to the chat that only you can see:
minescript.echo("Hello, world!")

# Write a chat message that other players can see:
minescript.chat("Hello, everyone!")

# Get your player's current position:
x, y, z = minescript.player().position

# Print information for the block that your player is standing on:
minescript.echo(minescript.getblock(x, y - 1, z))

# Set the block directly beneath your player (assuming commands are enabled):
x, y, z = int(x), int(y), int(z)
minescript.execute(f"setblock {x} {y-1} {z} yellow_concrete")

# Display the contents of your inventory:
for item in minescript.player_inventory():
  minescript.echo(item.item)
```

## Pre-built mod jars

Pre-built mod jars for Fabric, Forge, and NeoForge can be downloaded from
[Modrinth](https://modrinth.com/mod/minescript/versions) and
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/minescript/files).

## Command-line build instructions

To run the mod in dev mode, clone this repo:

```
$ git clone https://github.com/maxuser0/minescript.git
```

Then run the dev client for one of the supported mod loaders:

```
# Fabric client:
$ ./gradlew fabric:runClient

# Forge client:
$ ./gradlew forge:runClient

# NeoForge client:
$ ./gradlew neoforge:runClient
```

To build the mod without running it in dev mode, run:

```
# Build the Fabric mod:
$ ./gradlew fabric:build

# Build the Forge mod:
$ ./gradlew forge:build

# Build the NeoForge mod:
$ ./gradlew neoforge:build
```

The built mod jars will appear in `build/libs` within the given mod platform's subdirectory, e.g.

```
$ ls */build/libs/*-4.0.jar
fabric/build/libs/minescript-fabric-1.21.1-4.0.jar
forge/build/libs/minescript-forge-1.21.1-4.0.jar
neoforge/build/libs/minescript-neoforge-1.21.1-4.0.jar
```

## License

All code, compiled or in source form, in the built mod jar is licensed as GPL
v3 (specifically `SPDX-License-Identifier: GPL-3.0-only`). Sources within the
`tools` directory that are not distributed in the mod jar and are not required
for building or running the mod jar may be covered by a different license.

## Credits

Special thanks to **Spiderfffun** and **Coolbou0427** for extensive testing on Windows.
