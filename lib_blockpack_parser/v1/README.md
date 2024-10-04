## `lib_blockpack_parser v1`

Library for parsing BlockPack data.

&nbsp;

**Requirements**

  Minescript v3.1 or higher

&nbsp;

**Usage**

Functions for creating a BlockPackParser:

```
BlockPackParser.parse_blockpack(blockpack: BlockPack)
BlockPackParser.parse_base64_data(base64_data: str)
BlockPackParser.parse_binary_data(binary_data: bytes)
```

Classes:

```
class BlockPackParser:
  palette: List[str] # palette of block types
  tiles: List[Tile]

class Tile:
  offset: BlockPos # (x, y, z) offset of this tile
  def iter_fill_params(self) -> Tuple[BlockPos, BlockPos, int]
  def iter_setblock_params(self) -> Tuple[BlockPos, int]
```

&nbsp;

**Example**

Read blocks from (0, 0, 0) to (100, 100, 100) and print them to
the chat as `fill` and `setblock` commands:

```
from minescript import BlockPack, echo
from lib_blockpack_parser import BlockPackParser
blockpack = BlockPack.read_world((0, 0, 0), (100, 100, 100))
parser = BlockPackParser.parse_blockpack(blockpack)
for tile in parser.tiles:
  for pos1, pos2, block in tile.iter_fill_params():
    echo(f"fill {pos1} {pos2} {parser.palette[block]}")
  for pos, block in tile.iter_setblock_params():
    echo(f"setblock {pos} {parser.palette[block]}")
```
