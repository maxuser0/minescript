# SPDX-FileCopyrightText: © 2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""lib_blockpack_parser v1 distributed via minescript.net

Requires:
  minescript v3.1

Usage:
  Functions for creating a BlockPackParser:
    BlockPackParser.parse_blockpack(blockpack: BlockPack)
    BlockPackParser.parse_base64_data(base64_data: str)
    BlockPackParser.parse_binary_data(binary_data: bytes)

  class BlockPackParser:
    palette: List[str] # palette of block types
    tiles: List[Tile]

  class Tile:
    offset: BlockPos # (x, y, z) offset of this tile
    def iter_fill_params(self) -> Tuple[BlockPos, BlockPos, int]
    def iter_setblock_params(self) -> Tuple[BlockPos, int]

Example:
  from minescript import BlockPack, echo
  from blockpack_parser import BlockPackParser
  blockpack = BlockPack.read_world((0, 0, 0), (100, 100, 100))
  parser = BlockPackParser.parse_blockpack(blockpack)
  for tile in parser.tiles:
    for pos1, pos2, block in tile.iter_fill_params():
      echo(f"fill {pos1} {pos2} {parser.palette[block]}")
    for pos, block in tile.iter_setblock_params():
      echo(f"setblock {pos} {parser.palette[block]}")
"""

if __name__ == "__main__":
  # If running this module as a standalone script, print docstring and exit.
  import help
  import sys
  docstr = help.ReadDocString("lib_blockpack_parser.py")
  if docstr:
    print(docstr, file=sys.stderr)
    sys.exit(0)
  sys.exit(1)

import base64
import minescript
import struct
import sys

from array import array
from dataclasses import dataclass
from minescript import BlockPack, BlockPos
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

MASK_26_BITS = (1 << 26) - 1
MASK_12_BITS = (1 << 12) - 1

X_BUILD_MIN = -(1 << 25)
Y_BUILD_MIN = -64
Z_BUILD_MIN = -(1 << 25)


class BlockPackParserException(Exception):
  pass


@dataclass
class Chunk:
  length: int
  name: str
  data: bytes
  crc: bytes

  def read_bytes(self, num_bytes: int) -> bytes:
    result = self.data[:num_bytes]
    self.data = self.data[num_bytes:]
    return result


@dataclass
class Tile:
  offset: BlockPos
  block_types: array # array of 32-bit ints
  fills: array # array of 16-bit ints
  setblocks: array # array of 16-bit ints

  def iter_fill_params(self) -> Tuple[BlockPos, BlockPos, int]:
    """Iterates fill params.

    Yields:
      (BlockPos, BlockPos, int): two opposing corners of a fill volume and index
        into `BlockPackParser.palette`
    """
    for i in range(len(self.fills) // 3):
      x1, y1, z1 = self._unpack_16bit_pos(self.fills[3 * i])
      x2, y2, z2 = self._unpack_16bit_pos(self.fills[3 * i + 1])
      block = self.block_types[self.fills[3 * i + 2]]
      yield (
          (x1 + self.offset[0], y1 + self.offset[1], z1 + self.offset[2]),
          (x2 + self.offset[0], y2 + self.offset[1], z2 + self.offset[2]), block)

  def iter_setblock_params(self) -> Tuple[BlockPos, int]:
    """Iterates setblock params.

    Yields:
      (BlockPos, int): setblock position and index into `BlockPackParser.palette`
    """
    for i in range(len(self.setblocks) // 2):
      x, y, z = self._unpack_16bit_pos(self.setblocks[2 * i])
      block = self.block_types[self.setblocks[2 * i + 1]]
      yield ((x + self.offset[0], y + self.offset[1], z + self.offset[2]), block)

  def _unpack_16bit_pos(self, pos: int) -> BlockPos:
    x = pos >> 10;
    y = (pos >> 5) & ((1 << 5) - 1);
    z = pos & ((1 << 5) - 1);
    return (x, y, z)


int32_struct = struct.Struct("!I")
int64_struct = struct.Struct("!Q")


def bytes_as_int32(data: bytes) -> int:
  return int32_struct.unpack(data)[0]


def bytes_as_int64(data: bytes) -> int:
  return int64_struct.unpack(data)[0]


class _BlockPackChunkParser:
  def __init__(self, binary_data: bytes):
    self._data: bytes = binary_data
    self.palette: List[str] = []
    self.tiles: List[Tile] = []
    self.chunks: List[Chunk] = []
    self._parse_blockpack_data()

  def _parse_blockpack_data(self):
    magic: bytes = self._read_bytes(8)
    if magic.decode() != "BLOCPAK!":
      raise BlockPackParserException('Blockpack data does not begin with "BLOCPAK!"')
    done = False
    while not done:
      chunk = self._read_chunk()
      self.chunks.append(chunk)
      if chunk.name == "Plte":
        self._read_palette_chunk(chunk)
      elif chunk.name == "Tile":
        self._read_tile_chunk(chunk)
      elif chunk.name == "Done":
        done = True

  def _read_palette_chunk(self, chunk: Chunk):
    num_block_types: int = bytes_as_int32(chunk.read_bytes(4))
    for i in range(num_block_types):
      strlen: int = bytes_as_int32(chunk.read_bytes(4))
      self.palette.append(chunk.read_bytes(strlen).decode())

  def _read_tile_chunk(self, chunk: Chunk):
    num_tiles: int = bytes_as_int32(chunk.read_bytes(4))
    for i in range(num_tiles):
      tile_key: int = bytes_as_int64(chunk.read_bytes(8))
      x_offset: int = ((tile_key >> 26) & MASK_26_BITS) + X_BUILD_MIN
      y_offset: int = (tile_key >> (26 + 26)) + Y_BUILD_MIN
      z_offset: int = (tile_key & MASK_26_BITS) + Z_BUILD_MIN

      num_block_types = bytes_as_int32(chunk.read_bytes(4))
      block_types = array("I")
      block_types.frombytes(chunk.read_bytes(num_block_types * 4))

      num_fills = bytes_as_int32(chunk.read_bytes(4))
      fills = array("H")
      fills.frombytes(chunk.read_bytes(num_fills * 2))

      num_setblocks = bytes_as_int32(chunk.read_bytes(4))
      setblocks = array("H")
      setblocks.frombytes(chunk.read_bytes(num_setblocks * 2))

      if sys.byteorder != "big":
        # Swap to network (big-endian) byte order.
        block_types.byteswap()
        setblocks.byteswap()
        fills.byteswap()

      self.tiles.append(Tile((x_offset, y_offset, z_offset), block_types, fills, setblocks))

  def _read_chunk(self) -> Chunk:
    length = bytes_as_int32(self._read_bytes(4))
    name = self._read_bytes(4).decode()
    data = self._read_bytes(length)
    crc = self._read_bytes(4)
    return Chunk(length, name, data, crc)

  def _read_bytes(self, num_bytes: int) -> bytes:
    result = self._data[:num_bytes]
    self._data = self._data[num_bytes:]
    return result



class BlockPackParser:
  """Parser of blockpack data.

  An instance of `BlockPackParser` has two fields:
    `palette: List[str]` - list of block types
    `tiles: List[Tile]` - list of blockpack tiles
  """

  @classmethod
  def parse_blockpack(cls, blockpack: BlockPack) -> 'BlockPackParser':
    """Creates a BlockPackParser from a BlockPack."""
    return BlockPackParser(base64.b64decode(blockpack.export_data()))

  @classmethod
  def parse_base64_data(cls, base64_data: str) -> 'BlockPackParser':
    """Creates a BlockPackParser from a base64-encoded string."""
    return BlockPackParser(base64.b64decode(base64_data))

  @classmethod
  def parse_binary_data(cls, binary_data: bytes) -> 'BlockPackParser':
    """Creates a BlockPackParser from binary data."""
    return BlockPackParser(binary_data)

  def __init__(self, binary_data: bytes):
    """Creates a `BlockPackParser` from binary data.

    (__internal__)
    """
    parser = _BlockPackChunkParser(binary_data)
    self.palette: List[str] = parser.palette
    self.tiles: List[Tile] = parser.tiles
