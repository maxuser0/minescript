# SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""sphere v2 distributed via minescript.net

Requires:
  minescript v3.1

Usage:
  \sphere X Y Z RADIUS BLOCK_TYPE

Builds the surface of a sphere centered at location (X, Y, Z)
with radius RADIUS made of BLOCK_TYPE.

Player must be able to run the `/setblock` and `/fill` commands.

Example:
  Creates a sphere centered at the current player with
  radius 20 made of yellow concrete:

  \sphere ~ ~ ~ 20 yellow_concrete
"""

import math
import sys

from minescript import echo, BlockPacker, BlockPos

def create_sphere(r):
  """
  Args:
    r: radius (int)
  Returns:
    set of (x, y, z) coords along surface of sphere
  """
  surface = set()
  for arc1 in range(round(2 * math.pi * r)):
    theta = arc1 / r
    for arc2 in range(round(math.pi * r)):
      phi = arc2 / r - math.pi / 2
      x = round(r * math.cos(theta) * math.cos(phi))
      y = round(r * math.sin(theta) * math.cos(phi))
      z = round(r * math.sin(phi))
      surface.add((x, y, z))
  return surface

def run(args):
  x_origin = int(eval(args[1]))
  y_origin = int(eval(args[2]))
  z_origin = int(eval(args[3]))
  r = int(args[4])
  block = args[5]

  blockpacker = BlockPacker()
  surface = create_sphere(r)
  num_blocks = len(surface)
  i = 0
  for sx, sy, sz in surface:
    i += 1
    x = sx + x_origin
    y = sy + y_origin
    z = sz + z_origin
    op = "" if len(args) < 7 else (" " + " ".join(args[6:]))
    blockpacker.setblock((x, y, z), block)
    if i % 100 == 0:
      echo(f"Blocks placed: {i} of {num_blocks}")

  blockpacker.pack().write_world()
  echo(f"Created sphere surface with {num_blocks} blocks.")

if __name__ == "__main__":
  run(sys.argv)
