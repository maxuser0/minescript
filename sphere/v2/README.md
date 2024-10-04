## `sphere v2`

Builds the surface of a sphere out of blocks.

&nbsp;

**Requirements**

  minescript v3.1
  command execution: `/setblock`, `/fill`

&nbsp;

**Usage**

```
\sphere X Y Z RADIUS BLOCK_TYPE
```

Builds the surface of a sphere centered at location (X, Y, Z) with radius RADIUS made of BLOCK_TYPE.

Version 2 is significantly faster than version 1 due to the use of
[BlockPacker](https://minescript.net/docs/#blockpacker) and
[BlockPack](https://minescript.net/docs/#blockpack) which were introduced in Minescript v3.1.

&nbsp;

**Example**

Creates a sphere centered at the current player with radius 20 made of yellow concrete:

```
\sphere ~ ~ ~ 20 yellow_concrete
```

**Author:** maxuser
