# SPDX-FileCopyrightText: © 2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""blocks_to_image v1 distributed via minescript.net

Requires:
  minescript v3.1
  lib_blockpack_parser v1

Usage:
  \blocks_to_image X1 Y1 Z1 X2 Y2 Z2 LABEL [dscale=DSCALE]

Generates a 2D image in X and Z corresponding to the
top-down view of the volume of blocks from (X1, Y1, Z1) to (X2,
Y2, Z2). The generated image is named `<LABEL>.png`, along
with a depth map at `<LABEL>-depth.png` and a metadata file
`<LABEL>`.json containing a palette that reflects the mapping
between RGB color values and block types.

If dscale is provided, depth values in Y are scaled by <DSCALE>.
"""

import lib_blockpack_parser
import json
import minescript
import os
import random
import re
import sys
import time

from array import array
from minescript import BlockPack, BlockPos
from lib_blockpack_parser import BlockPackParser
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

try:
  import png
except ImportError:
  echo(
      "Python `png` module not installed. Try running the following from a terminal first:")
  echo("  pip install pypng")
  echo("or:")
  echo("  pip3 install pypng")
  sys.exit(2)

RGB = Tuple[int, int, int]

def run(argv):
  x1 = int(argv[1])
  y1 = int(argv[2])
  z1 = int(argv[3])
  x2 = int(argv[4])
  y2 = int(argv[5])
  z2 = int(argv[6])

  label = argv[7]

  dscale = 1
  if len(argv) > 8:
    dscale_param = argv[8]
    dscale_re = re.compile(r"dscale=([0-9.]+)")
    m = dscale_re.match(dscale_param)
    if not m:
      raise Exception(f"Expected param 8 to be `dscale=N` but got `{dscale_param}`")
    dscale = float(m.group(1))

  minX, minY, minZ = min(x1, x2), min(y1, y2), min(z1, z2)
  maxX, maxY, maxZ = max(x1, x2), max(y1, y2), max(z1, z2)
  blockpack = BlockPack.read_world((x1, y1, z1), (x2, y2, z2), offset=(-minX, -minY, -minZ))

  dx = maxX - minX + 1
  dy = maxY - minY + 1
  dz = maxZ - minZ + 1

  color_row_init_values = [0] * 3 * dx
  color_rows: List[array] = [array("B", color_row_init_values) for z in range(dz)]

  depth_row_init_values = [0] * dx
  depth_rows: List[array] = [array("B", depth_row_init_values) for z in range(dz)]

  block_colors: Dict[int, RGB] = {}
  num_pixels_filled = 0

  def block_id_to_rgb(block: int) -> RGB:
    rgb = block_colors.get(block)
    if rgb is None:
      # TODO(maxuser): Come up with better heuristics, or palette, for assigning RGB values.
      while True:
        rgb = (random.randint(0, 255), random.randint(0, 255), random.randint(0, 255))
        if rgb not in block_colors.values():
          break
      block_colors[block] = rgb
    return rgb

  def process_block(x: int, y: int, z: int, block: int):
    nonlocal num_pixels_filled
    depth = depth_rows[z][x]
    scaled_y = int(y * dscale)
    if scaled_y >= depth:
      num_pixels_filled += 1
      depth_rows[z][x] = scaled_y
      red, green, blue = block_id_to_rgb(block)
      color_rows[z][3 * x] = red
      color_rows[z][3 * x + 1] = green
      color_rows[z][3 * x + 2] = blue

  start_time = time.time()
  parser = BlockPackParser.parse_blockpack(blockpack)
  minescript.echo(
      f"Blockpack has {len(parser.palette)} block types across {len(parser.tiles)} tiles")
  for tile in parser.tiles:
    for (x1, y1, z1), (x2, y2, z2), block in tile.iter_fill_params():
      minX, minY, minZ = min(x1, x2), min(y1, y2), min(z1, z2)
      maxX, maxY, maxZ = max(x1, x2), max(y1, y2), max(z1, z2)
      for z in range(minZ, maxZ + 1):
        for x in range(minX, maxX + 1):
          process_block(x, maxY, z, block)

    for (x, y, z), block in tile.iter_setblock_params():
      process_block(x, y, z, block)

  t = time.time()
  minescript.echo(f"time: {t - start_time} seconds to fill {num_pixels_filled} pixels from {dx} x {dy} x {dz} volume")

  color_map_filename = os.path.join("minescript", label + ".png")
  with open(color_map_filename, "wb") as f:
    writer = png.Writer(dx, dz, greyscale=False)
    writer.write(f, color_rows)

  depth_map_filename = os.path.join("minescript", label + "-depth.png")
  with open(depth_map_filename, "wb") as f:
    writer = png.Writer(dx, dz, greyscale=True)
    writer.write(f, depth_rows)

  blocks = {
    "#%02x%02x%02x" % (rgb[0], rgb[1], rgb[2]) : parser.palette[block_id]
        for block_id, rgb in block_colors.items()
  }
  json_data = {}
  json_data["orientation"] = "x,z,y"
  json_data["color_map"] = os.path.basename(color_map_filename)
  json_data["depth_map"] = os.path.basename(depth_map_filename)
  json_data["depth_scale"] = dscale
  json_data["palette"] = [{"min_alpha": 64, "blocks": blocks}]
  json_filename = os.path.join("minescript", label + ".json")
  with open(json_filename, "w") as f:
    json.dump(json_data, f, indent=2)

  minescript.echo(f"time: {time.time() - t} seconds to write PNG files")


if __name__ == "__main__":
  run(sys.argv)
