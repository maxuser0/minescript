# SPDX-FileCopyrightText: © 2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""chessbot v1 distributed via minescript.net

Requires:
  minescript v4.0

Required commands: setblock, summon, tp

Usage:
```
\chessbot init - Reset the board that the player is looking at, otherwise create a new board.
\chessbot play - Start playing a game at the board the player is looking at.
\chessbot export - Print game state in Forsyth-Edwards Notation for game that player is viewing.
```

`play` by default is a local human-vs-human game. The following modes are also supported as an
additional param in the form of a string of length 2 where the first is the white-player mode and
the second the black-player mode:

- `h`: human player (with move validation)
- `m`: manual human player (no move validation)
- `c`: computer player

e.g. `\chessbot play hc` starts a game with a local human player as white and a local computer
player as black.

Right-click on a piece in the player's crosshairs to select it. Then move the selected piece by
right-clicking again on another piece or square.
"""

from dataclasses import dataclass
import random
import sys
import time
from minescript import (
  BlockPos,
  EntityData,
  EventQueue,
  EventType,
  TargetedBlock,
  Vector3f,
  echo,
  entities,
  execute,
  getblock,
  getblocklist,
  player_get_targeted_block,
  player_get_targeted_entity)
from enum import Enum
from typing import Dict, List, Optional

try:
  import chess
  using_chess_lib = True
except ImportError:
  using_chess_lib = False

def check_chess_lib():
  if not using_chess_lib:
    print(
        "Cannot import chess module so moves cannot be validated\n" +
        "or computer generated. Install with: pip3 install chess",
        file=sys.stderr)

# Commands for armor stands that represent the chess pieces.
# Each command has 3 float placeholders which can be populated as:
#   execute(SUMMON_PIECE_COMMANDS["white-pawn"] % (x, y, z))
SUMMON_PIECE_COMMANDS = {
  "white-pawn": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"white-pawn"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.8594f,0.0000f,0.0000f,-0.4375f,0.0000f,0.5586f,0.0000f,-2.0501f,0.0000f,0.0000f,0.8594f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.6016f,0.0000f,0.0000f,-0.3086f,0.0000f,0.6875f,0.0000f,-1.9642f,0.0000f,0.0000f,0.6016f,-0.3086f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.3438f,0.0000f,0.0000f,-0.1797f,0.0000f,0.5156f,0.0000f,-1.6634f,0.0000f,0.0000f,0.3438f,-0.1797f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.5156f,0.0000f,0.0000f,-0.2656f,0.0000f,0.2578f,0.0000f,-1.1478f,0.0000f,0.0000f,0.5156f,-0.2656f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.4297f,0.0000f,0.0000f,-0.2227f,0.0000f,0.4297f,0.0000f,-0.9759f,0.0000f,0.0000f,0.4297f,-0.2227f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.2578f,0.0000f,0.0000f,-0.1367f,0.0000f,0.2578f,0.0000f,-1.0189f,0.0000f,0.0000f,0.2578f,-0.1367f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "black-pawn": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"black-pawn"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8594f,0.0000f,0.0000f,-0.4375f,0.0000f,0.5586f,0.0000f,-2.0501f,0.0000f,0.0000f,0.8594f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.6016f,0.0000f,0.0000f,-0.3086f,0.0000f,0.6875f,0.0000f,-1.9642f,0.0000f,0.0000f,0.6016f,-0.3086f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.3438f,0.0000f,0.0000f,-0.1797f,0.0000f,0.5156f,0.0000f,-1.6634f,0.0000f,0.0000f,0.3438f,-0.1797f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.5156f,0.0000f,0.0000f,-0.2656f,0.0000f,0.2578f,0.0000f,-1.1478f,0.0000f,0.0000f,0.5156f,-0.2656f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.4297f,0.0000f,0.0000f,-0.2227f,0.0000f,0.4297f,0.0000f,-0.9759f,0.0000f,0.0000f,0.4297f,-0.2227f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.2578f,0.0000f,0.0000f,-0.1367f,0.0000f,0.2578f,0.0000f,-1.0189f,0.0000f,0.0000f,0.2578f,-0.1367f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "white-king": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"white-king"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[1.1250f,0.0000f,0.0000f,-0.5625f,0.0000f,0.8125f,0.0000f,-2.0000f,0.0000f,0.0000f,1.1250f,-0.5625f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.8750f,0.0000f,0.0000f,-0.4375f,0.0000f,0.7500f,0.0000f,-1.6875f,0.0000f,0.0000f,0.8750f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.6250f,0.0000f,0.0000f,-0.3125f,0.0000f,0.6875f,0.0000f,-1.3750f,0.0000f,0.0000f,0.6250f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.8750f,0.0000f,0.0000f,-0.4375f,0.0000f,0.2500f,0.0000f,-0.1250f,0.0000f,0.0000f,0.8750f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.5000f,0.0000f,0.0000f,-0.2500f,0.0000f,0.5625f,0.0000f,-0.6875f,0.0000f,0.0000f,0.5000f,-0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.6250f,0.0000f,0.0000f,-0.3125f,0.0000f,0.3750f,0.0000f,0.0000f,0.0000f,0.0000f,0.6250f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.3750f,0.0000f,0.1875f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.8750f,0.0000f,0.0000f,-0.4375f,0.0000f,0.3750f,0.0000f,0.3750f,0.0000f,0.0000f,0.8750f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.1250f,0.0000f,0.0000f,-0.0625f,0.0000f,0.3750f,0.0000f,0.5625f,0.0000f,0.0000f,0.3125f,-0.1875f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.1550f,0.0000f,0.0000f,-0.0760f,0.0000f,0.0000f,-0.3125f,1.0625f,0.0000f,0.5625f,0.0000f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.1250f,0.0000f,0.0000f,-0.0625f,0.0000f,0.2500f,0.0000f,0.9375f,0.0000f,0.0000f,0.1875f,-0.1250f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.1144f,0.0000f,0.0000f,-0.0584f,0.0000f,0.0884f,0.0893f,1.1009f,0.0000f,-0.0884f,0.0893f,-0.0325f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "black-king": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"black-king"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[1.1250f,0.0000f,0.0000f,-0.5625f,0.0000f,0.8125f,0.0000f,-2.0000f,0.0000f,0.0000f,1.1250f,-0.5625f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8750f,0.0000f,0.0000f,-0.4375f,0.0000f,0.7500f,0.0000f,-1.6875f,0.0000f,0.0000f,0.8750f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.6250f,0.0000f,0.0000f,-0.3125f,0.0000f,0.6875f,0.0000f,-1.3750f,0.0000f,0.0000f,0.6250f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8750f,0.0000f,0.0000f,-0.4375f,0.0000f,0.2500f,0.0000f,-0.1250f,0.0000f,0.0000f,0.8750f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.5000f,0.0000f,0.0000f,-0.2500f,0.0000f,0.5625f,0.0000f,-0.6875f,0.0000f,0.0000f,0.5000f,-0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.6250f,0.0000f,0.0000f,-0.3125f,0.0000f,0.3750f,0.0000f,0.0000f,0.0000f,0.0000f,0.6250f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.3750f,0.0000f,0.1875f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8750f,0.0000f,0.0000f,-0.4375f,0.0000f,0.3750f,0.0000f,0.3750f,0.0000f,0.0000f,0.8750f,-0.4375f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.1250f,0.0000f,0.0000f,-0.0625f,0.0000f,0.3750f,0.0000f,0.5625f,0.0000f,0.0000f,0.3125f,-0.1875f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.1550f,0.0000f,0.0000f,-0.0760f,0.0000f,0.0000f,-0.3125f,1.0625f,0.0000f,0.5625f,0.0000f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.1250f,0.0000f,0.0000f,-0.0625f,0.0000f,0.2500f,0.0000f,0.9375f,0.0000f,0.0000f,0.1875f,-0.1250f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.1144f,0.0000f,0.0000f,-0.0584f,0.0000f,0.0884f,0.0893f,1.1009f,0.0000f,-0.0884f,0.0893f,-0.0325f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "white-queen": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"white-queen"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[1.0547f,0.0000f,0.0000f,-0.5313f,0.0000f,0.7617f,0.0000f,-2.0000f,0.0000f,0.0000f,1.0547f,-0.5312f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.8203f,0.0000f,0.0000f,-0.4141f,0.0000f,0.7031f,0.0000f,-1.7070f,0.0000f,0.0000f,0.8203f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.5859f,0.0000f,0.0000f,-0.2969f,0.0000f,0.6445f,0.0000f,-1.4141f,0.0000f,0.0000f,0.5859f,-0.2969f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.8203f,0.0000f,0.0000f,-0.4141f,0.0000f,0.2344f,0.0000f,-0.2422f,0.0000f,0.0000f,0.8203f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.4688f,0.0000f,0.0000f,-0.2383f,0.0000f,0.5273f,0.0000f,-0.7695f,0.0000f,0.0000f,0.4688f,-0.2383f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.5859f,0.0000f,0.0000f,-0.2969f,0.0000f,0.3516f,0.0000f,-0.1250f,0.0000f,0.0000f,0.5859f,-0.2969f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.7031f,0.0000f,0.0000f,-0.3555f,0.0000f,0.3516f,0.0000f,0.0508f,0.0000f,0.0000f,0.7031f,-0.3555f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.8203f,0.0000f,0.0000f,-0.4141f,0.0000f,0.3516f,0.0000f,0.2266f,0.0000f,0.0000f,0.8203f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0507f,-0.0829f,0.0829f,-0.4141f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0000f,0.1657f,0.1657f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0507f,-0.0829f,0.0829f,-0.4141f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0000f,0.1657f,0.1657f,0.0547f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0481f,-0.0829f,0.0829f,-0.4165f,0.0278f,0.1435f,-0.1435f,0.4023f,0.0000f,0.1657f,0.1657f,-0.1797f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0007f,0.1645f,0.1669f,-0.4129f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0507f,0.0852f,-0.0805f,0.3908f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0007f,0.1645f,0.1669f,0.0558f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0507f,0.0852f,-0.0805f,0.3975f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0007f,0.1645f,0.1669f,-0.1786f,0.0278f,0.1435f,-0.1435f,0.4023f,-0.0481f,0.0852f,-0.0805f,0.3966f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.0507f,0.0852f,-0.0805f,0.3820f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0007f,-0.1645f,-0.1669f,0.4051f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.0507f,0.0852f,-0.0805f,0.3887f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0007f,-0.1645f,-0.1669f,-0.0636f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.0481f,0.0852f,-0.0805f,0.3878f,0.0278f,0.1435f,-0.1435f,0.4023f,-0.0007f,-0.1645f,-0.1669f,0.1707f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.0007f,-0.1645f,-0.1669f,0.4051f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0507f,-0.0852f,0.0805f,-0.4134f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.0007f,-0.1645f,-0.1669f,-0.0636f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0507f,-0.0852f,0.0805f,-0.4202f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.0007f,-0.1645f,-0.1669f,0.1707f,0.0278f,0.1435f,-0.1435f,0.4023f,0.0481f,-0.0852f,0.0805f,-0.4192f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.2344f,0.0000f,0.0000f,-0.1211f,0.0000f,0.2344f,0.0000f,0.4023f,0.0000f,0.0000f,0.2344f,-0.1211f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "black-queen": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"black-queen"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[1.0547f,0.0000f,0.0000f,-0.5313f,0.0000f,0.7617f,0.0000f,-2.0000f,0.0000f,0.0000f,1.0547f,-0.5312f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8203f,0.0000f,0.0000f,-0.4141f,0.0000f,0.7031f,0.0000f,-1.7070f,0.0000f,0.0000f,0.8203f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.5859f,0.0000f,0.0000f,-0.2969f,0.0000f,0.6445f,0.0000f,-1.4141f,0.0000f,0.0000f,0.5859f,-0.2969f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8203f,0.0000f,0.0000f,-0.4141f,0.0000f,0.2344f,0.0000f,-0.2422f,0.0000f,0.0000f,0.8203f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.4688f,0.0000f,0.0000f,-0.2383f,0.0000f,0.5273f,0.0000f,-0.7695f,0.0000f,0.0000f,0.4688f,-0.2383f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.5859f,0.0000f,0.0000f,-0.2969f,0.0000f,0.3516f,0.0000f,-0.1250f,0.0000f,0.0000f,0.5859f,-0.2969f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.7031f,0.0000f,0.0000f,-0.3555f,0.0000f,0.3516f,0.0000f,0.0508f,0.0000f,0.0000f,0.7031f,-0.3555f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8203f,0.0000f,0.0000f,-0.4141f,0.0000f,0.3516f,0.0000f,0.2266f,0.0000f,0.0000f,0.8203f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.0507f,-0.0829f,0.0829f,-0.4141f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0000f,0.1657f,0.1657f,-0.4141f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.0507f,-0.0829f,0.0829f,-0.4141f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0000f,0.1657f,0.1657f,0.0547f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.0481f,-0.0829f,0.0829f,-0.4165f,0.0278f,0.1435f,-0.1435f,0.4023f,0.0000f,0.1657f,0.1657f,-0.1797f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.0007f,0.1645f,0.1669f,-0.4129f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0507f,0.0852f,-0.0805f,0.3908f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.0007f,0.1645f,0.1669f,0.0558f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0507f,0.0852f,-0.0805f,0.3975f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.0007f,0.1645f,0.1669f,-0.1786f,0.0278f,0.1435f,-0.1435f,0.4023f,-0.0481f,0.0852f,-0.0805f,0.3966f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0507f,0.0852f,-0.0805f,0.3820f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0007f,-0.1645f,-0.1669f,0.4051f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0507f,0.0852f,-0.0805f,0.3887f,0.0293f,0.1435f,-0.1435f,0.4023f,-0.0007f,-0.1645f,-0.1669f,-0.0636f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0481f,0.0852f,-0.0805f,0.3878f,0.0278f,0.1435f,-0.1435f,0.4023f,-0.0007f,-0.1645f,-0.1669f,0.1707f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0007f,-0.1645f,-0.1669f,0.4051f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0507f,-0.0852f,0.0805f,-0.4134f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0007f,-0.1645f,-0.1669f,-0.0636f,0.0293f,0.1435f,-0.1435f,0.4023f,0.0507f,-0.0852f,0.0805f,-0.4202f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0007f,-0.1645f,-0.1669f,0.1707f,0.0278f,0.1435f,-0.1435f,0.4023f,0.0481f,-0.0852f,0.0805f,-0.4192f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.2344f,0.0000f,0.0000f,-0.1211f,0.0000f,0.2344f,0.0000f,0.4023f,0.0000f,0.0000f,0.2344f,-0.1211f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "white-rook": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"white-rook"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[1.0000f,0.0000f,0.0000f,-0.5000f,0.0000f,0.5625f,0.0000f,-2.0000f,0.0000f,0.0000f,1.0000f,-0.5000f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.8125f,0.0000f,-1.9375f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.6250f,0.0000f,0.0000f,-0.3125f,0.0000f,0.6875f,0.0000f,-1.5625f,0.0000f,0.0000f,0.6250f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.1875f,0.0000f,-0.6250f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.5000f,0.0000f,0.0000f,-0.2500f,0.0000f,0.2500f,0.0000f,-0.5625f,0.0000f,0.0000f,0.0625f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.0000f,0.0000f,0.0625f,-0.3750f,0.0000f,0.2500f,0.0000f,-0.5625f,-0.5000f,0.0000f,0.0000f,0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.5000f,0.0000f,0.0000f,0.2500f,0.0000f,0.2500f,0.0000f,-0.5625f,-0.0000f,0.0000f,-0.0625f,0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.0000f,0.0000f,-0.0625f,0.3750f,0.0000f,0.2500f,0.0000f,-0.5625f,0.5000f,0.0000f,-0.0000f,-0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[0.5625f,0.0000f,0.0000f,-0.2875f,0.0000f,0.3750f,0.0000f,-1.0000f,0.0000f,0.0000f,0.5625f,-0.2875f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "black-rook": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"black-rook"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[1.0000f,0.0000f,0.0000f,-0.5000f,0.0000f,0.5625f,0.0000f,-2.0000f,0.0000f,0.0000f,1.0000f,-0.5000f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.8125f,0.0000f,-1.9375f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.6250f,0.0000f,0.0000f,-0.3125f,0.0000f,0.6875f,0.0000f,-1.5625f,0.0000f,0.0000f,0.6250f,-0.3125f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.1875f,0.0000f,-0.6250f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.5000f,0.0000f,0.0000f,-0.2500f,0.0000f,0.2500f,0.0000f,-0.5625f,0.0000f,0.0000f,0.0625f,-0.3735f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.0000f,0.0000f,0.0625f,-0.3652f,0.0000f,0.2500f,0.0000f,-0.5625f,-0.5000f,0.0000f,0.0000f,0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[-0.5000f,0.0000f,0.0000f,0.2500f,0.0000f,0.2500f,0.0000f,-0.5625f,-0.0000f,0.0000f,-0.0625f,0.3678f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[-0.0000f,0.0000f,-0.0625f,0.3686f,0.0000f,0.2500f,0.0000f,-0.5625f,0.5000f,0.0000f,-0.0000f,-0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.5625f,0.0000f,0.0000f,-0.2875f,0.0000f,0.3750f,0.0000f,-1.0000f,0.0000f,0.0000f,0.5625f,-0.2875f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "white-bishop": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"white-bishop"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[-0.3750f,0.0000f,-0.0000f,0.1875f,0.0000f,0.9375f,0.0000f,-1.7500f,0.0000f,0.0000f,-0.3750f,0.1875f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-1.0000f,0.0000f,-0.0000f,0.5000f,0.0000f,0.5625f,0.0000f,-2.0000f,0.0000f,0.0000f,-1.0000f,0.5000f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.7500f,0.0000f,-0.0000f,0.3750f,0.0000f,0.8125f,0.0000f,-1.9375f,0.0000f,0.0000f,-0.7500f,0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_pillar",Properties:{axis:"y"}},transformation:[-0.5000f,0.0000f,-0.0000f,0.2500f,0.0000f,0.6875f,0.0000f,-2.0000f,0.0000f,0.0000f,-0.5000f,0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.7500f,0.0000f,-0.0000f,0.3750f,0.0000f,0.1250f,0.0000f,-0.8125f,0.0000f,0.0000f,-0.7500f,0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_stairs",Properties:{facing:"east",half:"bottom",shape:"straight"}},transformation:[-0.6875f,-0.0000f,-0.0000f,0.3586f,-0.0000f,0.6875f,0.0000f,-0.7656f,0.0000f,0.0000f,-0.4760f,0.2319f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.1768f,-0.2652f,-0.0000f,0.4556f,-0.1768f,0.2652f,0.0000f,-0.4034f,0.0000f,0.0000f,-0.4375f,0.2180f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.3094f,-0.2652f,-0.0000f,0.2023f,-0.3094f,0.2652f,0.0000f,-0.1414f,0.0000f,0.0000f,-0.4375f,0.2100f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[-0.3125f,0.0000f,-0.0000f,0.1875f,0.0000f,0.3125f,0.0000f,-0.1250f,0.0000f,0.0000f,-0.3125f,0.1500f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "black-bishop": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"black-bishop"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.3750f,0.0000f,0.0000f,-0.1875f,0.0000f,0.5625f,0.0000f,-1.3125f,0.0000f,0.0000f,0.3750f,-0.1875f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[1.0000f,0.0000f,0.0000f,-0.5000f,0.0000f,0.5625f,0.0000f,-2.0000f,0.0000f,0.0000f,1.0000f,-0.5000f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.8125f,0.0000f,-1.9375f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_basalt",Properties:{axis:"y"}},transformation:[0.5000f,0.0000f,0.0000f,-0.2500f,0.0000f,0.6875f,0.0000f,-2.0000f,0.0000f,0.0000f,0.5000f,-0.2500f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.7500f,0.0000f,0.0000f,-0.3750f,0.0000f,0.1250f,0.0000f,-0.8125f,0.0000f,0.0000f,0.7500f,-0.3750f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_stairs",Properties:{facing:"east",half:"bottom",shape:"straight"}},transformation:[0.6875f,0.0000f,0.0000f,-0.3586f,-0.0000f,0.6875f,0.0000f,-0.7656f,0.0000f,0.0000f,0.4760f,-0.2319f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.1768f,0.2652f,0.0000f,-0.4556f,-0.1768f,0.2652f,0.0000f,-0.4034f,0.0000f,0.0000f,0.4375f,-0.2180f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.3094f,0.2652f,0.0000f,-0.2023f,-0.3094f,0.2652f,0.0000f,-0.1414f,0.0000f,0.0000f,0.4375f,-0.2100f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0.3125f,0.0000f,0.0000f,-0.1875f,0.0000f,0.3125f,0.0000f,-0.1250f,0.0000f,0.0000f,0.3125f,-0.1500f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "white-knight": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"white-knight"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-1.1875f,0.0000f,-0.0000f,0.5875f,0.0000f,0.6680f,0.0000f,-2.0000f,0.0000f,0.0000f,-1.1875f,0.5875f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.8906f,0.0000f,-0.0000f,0.4391f,0.0000f,0.9648f,0.0000f,-1.9258f,0.0000f,0.0000f,-0.8906f,0.4391f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.8906f,0.0000f,-0.0000f,0.4391f,0.0000f,0.1484f,0.0000f,-1.4805f,0.0000f,0.0000f,-0.8906f,0.4391f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[-0.8164f,-0.0000f,-0.0000f,0.4195f,-0.0000f,0.8164f,0.0000f,-1.4248f,0.0000f,0.0000f,-0.5652f,0.2692f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_stairs",Properties:{facing:"east",half:"bottom",shape:"straight"}},transformation:[-0.0000f,0.8164f,-0.0000f,-0.3773f,0.8164f,0.0000f,0.0000f,-1.0352f,0.0000f,0.0000f,-0.3711f,0.1803f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz_slab",Properties:{type:"bottom"}},transformation:[0.3674f,-0.7347f,0.0000f,-0.0062f,-0.3674f,-0.7347f,-0.0000f,-0.6641f,0.0000f,0.0000f,-0.5560f,0.2658f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.3711f,0.0000f,0.0000f,-0.3031f,0.0000f,0.2227f,0.0000f,-0.2188f,0.0000f,0.0000f,0.1484f,-0.0805f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0742f,0.0000f,0.0000f,0.1422f,0.0000f,0.0742f,0.0000f,-0.3169f,0.0000f,0.0000f,0.0742f,-0.2515f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_block",Properties:{}},transformation:[0.0742f,0.0000f,0.0000f,0.1422f,0.0000f,0.0742f,0.0000f,-0.3067f,0.0000f,0.0000f,0.0742f,0.1566f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[-0.0000f,0.0000f,-0.1484f,0.5133f,0.0000f,0.5938f,0.0000f,-0.5898f,0.2969f,0.0000f,-0.0000f,-0.1547f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:quartz_slab",Properties:{type:"bottom"}},transformation:[0.0000f,-0.2227f,0.0000f,-0.3773f,0.7422f,0.0000f,0.0000f,-1.0352f,0.0000f,0.0000f,0.1484f,-0.0805f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
  "black-knight": """/summon armor_stand %f %f %f {Invisible:1b,DisabledSlots:4144959,CustomName:'[{"text":"black-knight"}]',Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[1.1875f,0.0000f,0.0000f,-0.5875f,0.0000f,0.6680f,0.0000f,-2.0000f,-0.0000f,0.0000f,1.1875f,-0.5875f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8906f,0.0000f,0.0000f,-0.4391f,0.0000f,1.0391f,0.0000f,-1.9258f,-0.0000f,0.0000f,0.8906f,-0.4391f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.8164f,0.0000f,0.0000f,-0.4195f,-0.0000f,0.8164f,0.0000f,-1.4248f,-0.0000f,-0.0000f,0.5652f,-0.2692f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_stairs",Properties:{facing:"east",half:"bottom",shape:"straight"}},transformation:[0.0000f,-0.8164f,0.0000f,0.3773f,0.8164f,0.0000f,0.0000f,-1.0352f,-0.0000f,0.0000f,0.3711f,-0.1803f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[-0.3674f,0.7347f,0.0000f,0.0062f,-0.3674f,-0.7347f,-0.0000f,-0.6641f,-0.0000f,-0.0000f,0.5560f,-0.2658f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[-0.3711f,0.0000f,-0.0000f,0.3031f,0.0000f,0.2227f,0.0000f,-0.2188f,0.0000f,0.0000f,-0.1484f,0.0805f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0742f,0.0000f,-0.0000f,-0.1422f,0.0000f,0.0742f,0.0000f,-0.3169f,0.0000f,0.0000f,-0.0742f,0.2515f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[-0.0742f,0.0000f,-0.0000f,-0.1422f,0.0000f,0.0742f,0.0000f,-0.3067f,0.0000f,0.0000f,-0.0742f,-0.1566f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[0.0000f,0.0000f,0.1484f,-0.5133f,0.0000f,0.5938f,0.0000f,-0.5898f,-0.2969f,0.0000f,0.0000f,0.1547f,0.0000f,0.0000f,0.0000f,1.0000f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:polished_blackstone_slab",Properties:{type:"bottom"}},transformation:[-0.0000f,0.2227f,-0.0000f,0.3773f,0.7422f,0.0000f,0.0000f,-1.0352f,0.0000f,-0.0000f,-0.1484f,0.0805f,0.0000f,0.0000f,0.0000f,1.0000f]}]}""",
}

PIECE_NAME_TO_SYMBOL = {
  "black-pawn": "p",
  "black-rook": "r",
  "black-knight": "n",
  "black-bishop": "b",
  "black-queen": "q",
  "black-king": "k",
  "white-pawn": "P",
  "white-rook": "R",
  "white-knight": "N",
  "white-bishop": "B",
  "white-queen": "Q",
  "white-king": "K",
}

PIECE_SYMBOL_TO_NAME = {
  "p": "black-pawn",
  "r": "black-rook",
  "n": "black-knight",
  "b": "black-bishop",
  "q": "black-queen",
  "k": "black-king",
  "P": "white-pawn",
  "R": "white-rook",
  "N": "white-knight",
  "B": "white-bishop",
  "Q": "white-queen",
  "K": "white-king",
}

WHITE_BLOCK = "minecraft:white_concrete"
BLACK_BLOCK = "minecraft:gray_concrete"

underboard_offsets = {
  "minecraft:yellow_concrete": (1.0, 1, 1.0),
  "minecraft:lime_concrete": (0.0, 1, 1.0),
  "minecraft:red_concrete": (1.0, 1, 0.0),
  "minecraft:blue_concrete": (0.0, 1, 0.0)
}

class PlayerMode(Enum):
  # TODO: Support REMOTE player: wait for move on board from outside this script. (Requires saving
  # turn state in world.)
  HUMAN: str = "h"
  MANUAL: str = "m"
  COMPUTER: str = "c"

  def is_controlled_by_local_player(self) -> bool:
    return self in (PlayerMode.HUMAN, PlayerMode.MANUAL)
  
  def requires_validation(self) -> bool:
    return self in (PlayerMode.HUMAN, PlayerMode.COMPUTER)


Color = bool
WHITE: Color = True
BLACK: Color = False


@dataclass(frozen=True)
class Square:
  file: int # 1-8 representing a-h
  rank: int # 1-8

  @staticmethod
  def uci(uci_str: str) -> "Square":
    return Square(file = ord(uci_str[0]) - ord('a') + 1, rank = ord(uci_str[1]) - ord('0'))

  @staticmethod
  def distance(sq1: "Square", sq2: "Square") -> int:
      """
      Returns the number of king steps from sq1 to sq2.
      """
      return max(abs(sq1.file - sq2.file), abs(sq1.rank - sq2.rank))

  def is_valid(self):
    r, f = self.rank, self.file
    return r >= 1 and r <= 8 and f >= 1 and f <= 8

  def name(self):
    if self.is_valid():
      return f"{chr(ord('a') - 1 + self.file)}{self.rank}"
    else:
      return f"[off-board: rank={self.rank}, file={self.file}]"


class GameException(Exception):
  """Exception type for invalid user input. Respond to user instructionally without stacktrace."""
  
  def __init__(self, message: str):
    super().__init__(message)


class EntityChecker:
  def is_valid_piece(self, turn: str, entity_name: str, square: Square) -> bool:
    """Returns True if `entity` at `square` is a valid chess piece in this game.

    Args:
      turn: "white" or "black"
      entity_name: name of selected entity, e.g. "black-knight"
      square: location of piece
    """

    # TODO: Verify that piece is a chess piece with a valid square on the board.
    return True


@dataclass
class BoardPlacement:
  topleft_corner: BlockPos
  capture_pos: List[Vector3f] = None # [white_capture_pos, black_capture_pos]
  
  def world_to_board(self, world_pos: Vector3f) -> Square:
    "Returns square on the board for the given world position."
    return Square(
        rank=round((world_pos[0] - self.topleft_corner[0]) / 2 + 0.5),
        file=round((world_pos[2] - self.topleft_corner[2]) / 2 + 0.5))
  
  def board_to_world(self, board_pos: Square) -> Vector3f:
    "Returns world position for the given square on the board."
    return [
      (board_pos.rank - 0.5) * 2 + self.topleft_corner[0],
      self.topleft_corner[1] + 1,
      (board_pos.file - 0.5) * 2 + self.topleft_corner[2]
    ]
  
  def next_capture_pos(self, color: Color):
    # Offset to ensure that coordinates aren't all ints because that offsets position by a half
    # block.
    epsilon = 0.001
    if color == WHITE:
      index = 0
      x_start = self.topleft_corner[0] + 1 + epsilon
      x_min = x_start - 1
      x_max = self.topleft_corner[0] + 8 + epsilon
      x_offset, z_offset = 1.25, -1.25  # Offset between captured pieces.
    else:
      index = 1
      x_start = self.topleft_corner[0] + 15 + epsilon
      x_min = self.topleft_corner[0] + 8 + epsilon
      x_max = x_start + 1
      x_offset, z_offset = -1.25, -1.25  # Offset between captured pieces.

    if not self.capture_pos:
      self.capture_pos = [None, None]
    if self.capture_pos[index] is None:
      self.capture_pos[index] = (x_start, self.topleft_corner[1] + 1, self.topleft_corner[2] - 2)

    pos = self.capture_pos[index]
    new_pos = list(self.capture_pos[index])
    new_pos[0] += x_offset
    if new_pos[0] < x_min or new_pos[0] > x_max:
      new_pos[0] = x_start
      new_pos[2] += z_offset
    self.capture_pos[index] = tuple(new_pos)
    return pos


def locate_board(x: int, y: int, z: int) -> BoardPlacement:
  "Returns BoardPlacement given the targeted_block the player is looking at, or None if not found."
  
  # Find the top-left of the board:
  # - first scan to the left (-x direction) to find the first non-board block
  # - then scan upward (-z direction) to find the first non-board block
  blocks_leftward = getblocklist([(x - i, y, z) for i in range(18)])
  if blocks_leftward[0] not in (WHITE_BLOCK, BLACK_BLOCK):
    return None
  end_of_board_found = False
  for i, block in enumerate(blocks_leftward[1:], 1):
    if block not in (WHITE_BLOCK, BLACK_BLOCK):
      end_of_board_found = True
      break
  if not end_of_board_found:
    raise GameException("Malformed board: too many white/gray concrete blocks found in a row.")
  min_board_x = x - i + 1

  blocks_upward = getblocklist([(min_board_x, y, z - i) for i in range(18)])
  if blocks_leftward[0] not in (WHITE_BLOCK, BLACK_BLOCK):
    raise GameException("Target a block along the surface of a chess board and try again.")
  end_of_board_found = False
  for i, block in enumerate(blocks_upward[1:], 1):
    if block not in (WHITE_BLOCK, BLACK_BLOCK):
      end_of_board_found = True
      break
  if not end_of_board_found:
    raise GameException("Malformed board: too many white/gray concrete blocks foun in a row.")
  min_board_z = z - i + 1

  return BoardPlacement(topleft_corner=(min_board_x, y, min_board_z))


def init_board(fen: Optional[str]):
  """Reset the board if the player is looking at one, otherwise create a new board.
  
  Args:
    fen: if specified, set the board based on FEN string, otheriwse using default start state
  """
  
  targeted_block = player_get_targeted_block()
  if not targeted_block:
    echo("Look at a block for initializing a board and run `chessbot init` again.")
    return

  board_placement = locate_board(*targeted_block.position)
  if board_placement is None:
    create_board(targeted_block)

    # Place pieces 1 block higher than targeted block to account for board creation.
    init_pos = list(targeted_block.position)
    init_pos[1] += 1
    board_placement = locate_board(*init_pos)
  set_piece_positions(board_placement, fen)
  

def create_board(targeted_block: TargetedBlock):
  """Create a new board of alternating light and dark blocks."""
  
  # When laying out the "underboard", place blocks in this orientation with
  # +x to the right and +z down: 
  # [ yellow_concrete ][ lime_concrete ]
  # [  red_concrete   ][ blue_concrete ]
  x, y, z = targeted_block.position
  for i in range(8):
    for j in range(8):
      left = x - 8 + 2 * i
      top = z - 8 + 2 * j
      block = WHITE_BLOCK if (i + j) % 2 else BLACK_BLOCK
      for di in range(2):
        for dj in range(2):
          execute(f"setblock {left + di} {y + 1} {top + dj} {block}")
      execute(f"setblock {left} {y} {top} yellow_concrete")
      execute(f"setblock {left} {y} {top + 1} red_concrete")
      execute(f"setblock {left + 1} {y} {top} lime_concrete")
      execute(f"setblock {left + 1} {y} {top + 1} blue_concrete")
    

def set_piece_positions(board_placement: BoardPlacement, fen: Optional[str]):
  """Reset an existing board's pieces.

  Args:
    board_placement: location of an existing board
    fen: FEN representation of the board to replicate, or the default start state if None
  """

  fen = fen or "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  
  z_offset = 5
  min_selection_pos = list(board_placement.topleft_corner)
  min_selection_pos[1] -= 1
  min_selection_pos[2] -= z_offset
  pieces = [
      p for p in entities(position=min_selection_pos, offset=(16, 3, 16 + z_offset))
          if "-" in p.name and p.name.split("-")[0] in ("black", "white")
  ]

  # Map from piece name to set of entities' data.
  piece_map: Dict[str, List[EntityData]] = {}
  for piece in pieces:
    piece_map.setdefault(piece.name, list()).append(piece)

  def place(piece_name: str, square: Square):
    block_pos = board_placement.board_to_world(square)
    pieces = piece_map.get(piece_name)
    if pieces:
      piece = pieces.pop()
      execute(f"tp {piece.uuid} {block_pos[0]} {block_pos[1]} {block_pos[2]}")
    else:
      execute(SUMMON_PIECE_COMMANDS[piece_name] % tuple(block_pos))

  fen_parts = fen.split()
  rank = 8
  file = 1
  for i, char in enumerate(fen_parts[0]):
    piece = PIECE_SYMBOL_TO_NAME.get(char)
    if piece is not None:
      place(piece, Square(file, rank))
      file += 1
    elif char == "/":
      rank -= 1
      file = 1
    else:
      try:
        skip_spaces = int(char)
        file += skip_spaces
      except:
        raise GameException(f"Unexpected character at position {i} of FEN `{fen_parts[0]}`: `{char}`")
  
  # TODO: Apply fen_parts[1:] (e.g. "w KQkq - 0 1") to world/block state.

  # Move remaining pieces that haven't been placed on the board to the same capture position, then
  # delete all the armor stands and block displays near that position.
  capture_pos = board_placement.next_capture_pos(WHITE)
  entities_to_delete = False
  for piece_entities in piece_map.values():
    for entity in piece_entities:
      entities_to_delete = True
      execute(f"tp {entity.uuid} {capture_pos[0]} {capture_pos[1]} {capture_pos[2]}")
  if entities_to_delete:
    x, y, z = [int(p) for p in capture_pos]
    execute(f"kill @e[type=block_display,x={x},y={y-1},z={z},dx=1,dy=4,dz=1]")
    execute(f"kill @e[type=armor_stand,x={x},y={y-1},z={z},dx=1,dy=4,dz=1]")
  

class ManualBoard:
  """Simple mock of chess.Board that doesn't do any validation."""

  def __init__(self):
    self.turn = WHITE

  def is_game_over(self):
    return False

  def update_turn(self):
    self.turn = not self.turn


class Game:
  def __init__(self, white_player_mode: PlayerMode, black_player_mode: PlayerMode):
    board_block = player_get_targeted_block()
    if not board_block:
      raise GameException("Target a square on a chess board and run `chessbot play` again.")

    self.white_player_mode = white_player_mode
    self.black_player_mode = black_player_mode
    self.entity_checker = EntityChecker()
    
    self.board_placement = locate_board(*board_block.position)
    if self.board_placement is None:
      raise GameException(
          "Target a block along the surface of a chess board and try again. " +
          "Or run `\\chess init` to create a board first.")

    if white_player_mode.requires_validation() or black_player_mode.requires_validation():
      fen = self.get_board_fen()
      self.board = chess.Board(fen)
      self.validate_moves = True
    else:
      self.board = ManualBoard()
      self.validate_moves = False

    # Register listener for mouse events for player moves.
    self.event_queue = EventQueue()
    self.event_queue.register_mouse_listener()

  def get_board_fen(self):
    """Return the state of the board in Forsyth-Edwards Notation (FEN).

    See: https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation
    """
    occupied_squares = self.get_occupied_squares()
    fen = ""
    for rank in range(8, 0, -1):
      num_blanks = 0
      for file in range(1, 9):
        piece = occupied_squares.get(Square(file, rank))
        if piece is None:
          num_blanks += 1
        else:
          if num_blanks > 0:
            fen += str(num_blanks)
            num_blanks = 0
          fen += PIECE_NAME_TO_SYMBOL[piece.name]
      if num_blanks > 0:
        fen += str(num_blanks)
      if rank != 1:
        fen += "/"
    
    # TODO: Read these from block/world state:
    fen += " w KQkq - 0 1"

    return fen
  
  def play(self):
    last_clicked_entity = None
    last_turn = BLACK
    while not self.board.is_game_over():
      player_mode = self.white_player_mode if self.board.turn == WHITE else self.black_player_mode
      if player_mode.is_controlled_by_local_player():
        if self.board.turn != last_turn:
          last_turn = self.board.turn
        event = self.event_queue.get()
        if event.type == EventType.MOUSE and event.action == 1 and event.button == 1:
          if last_clicked_entity is None:
            last_clicked_entity = player_get_targeted_entity()
            if last_clicked_entity:
              square = self.world_to_board(last_clicked_entity.position)
              if player_mode == PlayerMode.MANUAL:
                echo(f"Selected {last_clicked_entity.name} at {square.name()}.")
              elif self.entity_checker.is_valid_piece(
                  "white" if self.board.turn == WHITE else "black",
                  last_clicked_entity.name, square):
                echo(f"Selected {last_clicked_entity.name} at {square.name()}.")
              else:
                last_clicked_entity = None
          else:
            block = player_get_targeted_block()
            self.release_piece(last_clicked_entity, block, player_mode != PlayerMode.MANUAL)
            last_clicked_entity = None
      else:
        self.do_computer_move()
        last_turn = self.board.turn

    # TODO: Print a nicer message about the outcome, e.g. "White wins by checkmate."
    if self.validate_moves:
      echo(self.board.outcome())
  
  def move_piece(
      self, from_square: Square, to_square: Square, promotion: Optional[str],
      moving_piece: EntityData = None):
    """Moves the piece at from_square to to_square, potentially capturing a piece.

    Does not validate that the move is legal.

    Args:
      from_square: source of move
      to_square: destination of move
      promotion: string name of piece to promote to, e.g. "queen", or None for no promotion
      moving_piece: EntityData for piece being move (needed only if piece starts off board)
    """
    
    # TODO: Check move for en passant, castle (and castle eligibility), and pawn promotion. And
    # preserve game state in the world so that the game can be resumed across runs of the script.
    occupied_squares = self.get_occupied_squares()
    world_dest = self.board_to_world(to_square)
    moving_piece = moving_piece or occupied_squares[from_square]
    this_turn = not moving_piece.name.startswith("black-")
    captured = occupied_squares.get(to_square)
    if captured is not None:
      cap = self.board_placement.next_capture_pos(this_turn)
      execute(f"tp {captured.uuid} {cap[0]} {cap[1]} {cap[2]}")
    execute(f"tp {moving_piece.uuid} {world_dest[0]} {world_dest[1]} {world_dest[2]}")

    should_echo_move = (self.black_player_mode.is_controlled_by_local_player() or
        self.white_player_mode.is_controlled_by_local_player())

    if not from_square.is_valid():
      if to_square.is_valid():
        if self.validate_moves:
          piece_color, piece_name = moving_piece.name.split("-")
          self.board.set_piece_at(
              chess.parse_square(to_square.name()),
              chess.Piece(chess.PIECE_NAMES.index(piece_name), piece_color == "white"))
        if should_echo_move:
          echo(f"{moving_piece.name} dropped at {to_square.name()}.")
    elif not to_square.is_valid():
      if self.validate_moves:
        self.board.remove_piece_at(chess.parse_square(from_square.name()))
      if should_echo_move:
        echo(f"{moving_piece.name} removed from {from_square.name()}.")
    else:
      if self.validate_moves:
        chesslib_from_square = chess.parse_square(from_square.name())
        chesslib_to_square = chess.parse_square(to_square.name())
        move = chess.Move(
            chesslib_from_square,
            chesslib_to_square,
            promotion=chess.PIECE_NAMES.index(promotion))
        self.board.push(move)
      else:
        self.board.update_turn()
      if moving_piece.name.endswith("-king") and Square.distance(from_square, to_square) == 2:
        # TODO: Move rook when castling.
        echo(f"{moving_piece.name} castled: {from_square.name()}{to_square.name()}")
      elif should_echo_move:
        echo(f"{moving_piece.name} moved: {from_square.name()}{to_square.name()}")
    
    if to_square.is_valid():
      self.await_moving_piece(moving_piece.name, to_square)

    if promotion:
      color, old_piece_name = moving_piece.name.split("-")
      echo(f"Promoting {color} {old_piece_name} at {to_square.name()} to {promotion}.")

      # Put the pawn that got promoted into this turn's capture area.
      cap = self.board_placement.next_capture_pos(this_turn)
      execute(f"tp {moving_piece.uuid} {cap[0]} {cap[1]} {cap[2]}")

      block_pos = self.board_placement.board_to_world(to_square)
      promotion_with_color = f"{color}-{promotion}"
      execute(SUMMON_PIECE_COMMANDS[promotion_with_color] % tuple(block_pos))
      self.await_moving_piece(promotion_with_color, to_square)

  def await_moving_piece(self, moving_piece_name: str, to_square: Square):
    """Blocks until the moved piece appears in the destination square."""
    
    while True:
      occupied_squares = self.get_occupied_squares()
      piece_at_dest = occupied_squares.get(to_square)
      if piece_at_dest and piece_at_dest.name == moving_piece_name:
        break
      time.sleep(0.1)
  
  def do_computer_move(self):
    legal_moves = list(self.board.legal_moves)
    move = random.choice(legal_moves)
    uci = move.uci()
    from_square = Square.uci(uci[0:2])
    to_square = Square.uci(uci[2:4])
    promotion = chess.PIECE_NAMES[move.promotion] if move.promotion else None
    self.move_piece(from_square, to_square, promotion)
  
  def release_piece(
      self, selected_piece: EntityData, block: TargetedBlock, validate_move: bool
  ) -> bool:
    """Release the selected piece, checking for a valid move.
    
    Args:
      selected_piece: the selected piece that's being released
      block: targeted block on which the selected piece is being released
      validate_move: if True, move the piece only if the move is legal
    
    Returns:
      True if piece was moved.
    """
    
    color = "white" if self.board.turn == WHITE else "black"
    if not block:
      echo(f"No block selected for move. Still {color}'s turn.")
      return False

    bx, by, bz = [int(p) for p in block.position]
    underboard_block = getblock(bx, by - 1, bz)
    if underboard_block in underboard_offsets:
      offsets = underboard_offsets[underboard_block]
      new_pos = (bx + offsets[0], by + offsets[1], bz + offsets[2])
    elif not validate_move:
      # Allow piece to be moved off the board.
      new_pos = (bx, by + 1, bz)
    else:
      echo(f"Error: expected colored concrete under chess board but got {underboard_block}.")
      return False

    from_square = self.world_to_board(selected_piece.position)
    to_square = self.world_to_board(new_pos)

    # TODO: Let user choose the promotion piece instead of assuming it's a queen.
    promotion = "queen" if selected_piece.name.endswith("-pawn") and to_square.rank in (1, 8) else None

    if validate_move:
      if from_square.is_valid() and to_square.is_valid() and from_square != to_square:
        move = chess.Move.from_uci(from_square.name() + to_square.name() + (promotion or ""))
      else:
        move = chess.Move.null()

      if not to_square.is_valid():
        echo(f"Invalid move. Cannot move {selected_piece.name} off board. Still {color}'s turn. Try again.")
        return False
      
      if move not in self.board.legal_moves:
        if self.board.is_check():
          echo(f"Invalid move. King is in check. Still {color}'s turn.")
        else:
          echo(f"Invalid move. Still {color}'s turn.") 
        return False
      
    self.move_piece(from_square, to_square, promotion, selected_piece)
    return True
  
  def get_occupied_squares(self) -> Dict[Square, EntityData]:
      y = self.board_placement.topleft_corner[1] + 1
      return {
          self.world_to_board(p.position): p
              for p in entities(position=self.board_placement.topleft_corner, offset=(16, 2, 16))
                  if abs(p.position[1] - y) < 1 and "-" in p.name and p.name.split("-")[0] in ("black", "white")
      }
  
  def world_to_board(self, world_pos: Vector3f) -> Square:
    return self.board_placement.world_to_board(world_pos)
  
  def board_to_world(self, board_pos: Square) -> Vector3f:
    return self.board_placement.board_to_world(board_pos)


def main(argv):
  if len(argv) == 1:
    check_chess_lib()
    echo("chessbot usage:")
    echo(r"  \chessbot init - create chess board and pieces")
    echo(r"  \chessbot play [(h|c)(h|c)]- play/resume game")
    echo(r"  \chessbot export - game state in Forsyth-Edwards Notation")
    return 0
  
  action = argv[1]
  if action == "init":
    fen = argv[2] if len(argv) > 2 else None
    init_board(fen)
    return 0
  elif action == "play":
    check_chess_lib()
    player_mode_values = [m.value for m in PlayerMode]
    if using_chess_lib:
      white_player_mode = PlayerMode.HUMAN
      black_player_mode = PlayerMode.HUMAN
    else:
      white_player_mode = PlayerMode.MANUAL
      black_player_mode = PlayerMode.MANUAL
    if len(argv) > 2:
      players = argv[2]
      if len(players) != 2 or players[0] not in player_mode_values or players[1] not in player_mode_values:
        raise GameException(
            f"Expected `play` option to be a 2-letter combination of `h` (human), `c` (computer), and `m` (manual) but got: `{players}`")
      white_player_mode = PlayerMode(players[0])
      black_player_mode = PlayerMode(players[1])
      if not using_chess_lib and (
          white_player_mode.requires_validation() or black_player_mode.requires_validation()):
        print("Only manual mode is supported when chess module is unavailable.", file=sys.stderr)
        print(r"Play with: \chessbot play mm", file=sys.stderr)
        return 1
    game = Game(white_player_mode, black_player_mode)
    game.play()
    return 0
  elif action == "export":
    game = Game(PlayerMode.MANUAL, PlayerMode.MANUAL)
    echo(game.get_board_fen())
    return 0
  else:
    print(f"Unrecognized action `{action}`.", file=sys.stderr)
    print("Supported actions: init, play, export", file=sys.stderr)
    return 2
    

if __name__ == "__main__":
  try:
    sys.exit(main(sys.argv))
  except GameException as e:
    print(e, file=sys.stderr)