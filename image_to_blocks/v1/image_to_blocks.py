# SPDX-FileCopyrightText: © 2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""image_to_blocks v1 distributed via minescript.net

Requires:
  minescript v3.1

Usage:
  \image_to_blocks <x> <y> <z> <imagePngFile> \
      [<depthPngFile> [dscale=<depthScale>]] <orientation>

  \image_to_blocks <x> <y> <z> <imageSpecJsonFile> \
      [<imagePngFile>] [<depthPngFile> [dscale=<depthScale>]] \
      [<orientation>]

Loads the image at `imagePngFile` and sets blocks for each
pixel in the image. If `depthPngFile` is specified, its width and
height must match those of `imagePngFile`, and must be
greyscale-only format.

`depthScale` is an optional factor by which to divide
depth-image values. For example, a value of `dscale=25.5`
would map a depth-image value of 255 (where 0 is black and
255 is white) to 10.

`orientation` must be a comma-delimited string of world
dimensions with optional +/- sign. 2D example: "x,-y" maps
image x (first dimension) to world x and image y (second
dimension) to world -y; 3D example: "x,-z,y" maps image x (first
dimension) to world x, image y (second dimension) to world -z,
and image depth (third dimension) to world y.

`imageSpecJsonFile` can be used as a convenient way to
package a specification for converting an image to blocks.
The filename must end in ".json" and must contain a JSON
object with optional fields: "orientation", "color_map",
"depth_map", "depth_scale", and "palette". The "orientation"
field is formatted like the `orientation` param (see above). The
"color_map" and "depth_map" fields refer to PNG filenames.
The "depth_scale" field is a positive float in the range [0, 255]
for scaling down depth values. The "palette" field must be an
array of JSON objects with fields "min_alpha" (int in range [0,
255]) and "blocks"; "blocks" fields may be RGB values encoded
as strings with a leading "#", e.g. "#15b215", and values are
types of blocks, e.g. "green_wool" or
"oak_leaves[persistent=true]". Params passed to the
`image_to_blocks` command override corresponding entries
in the JSON file.
"""

import json
import minescript
import os
import re
import sys

from array import array
from dataclasses import dataclass
from minescript import echo, BlockPacker, BlockPos
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


@dataclass
class PaletteLayer:
  min_alpha: int  # in the range [0..255]
  blocks: Dict[int, str]


@dataclass
class Palette:
  layers: List[PaletteLayer]


@dataclass
class ImageSpec:
  orientation: str = None
  color_map: str = None
  depth_map: str = None
  depth_scale: float = None
  palette: Palette = None


default_palette = Palette(
  [
    PaletteLayer(
      min_alpha=127,
      blocks={
        0xced7d8: "white_concrete",
        0xff6101: "orange_concrete",
        0xc32ba2: "magenta_concrete",
        0x008bcb: "light_blue_concrete",
        0xffb300: "yellow_concrete",
        0x05ac00: "lime_concrete",
        0xe35e8f: "pink_concrete",
        0x31363c: "gray_concrete",
        0x7e7e73: "light_gray_concrete",
        0x00788a: "cyan_concrete",
        0x7419a0: "purple_concrete",
        0x292b94: "blue_concrete",
        0x6b3817: "brown_concrete",
        0x3f5c19: "green_concrete",
        0xa71c1b: "red_concrete",
        0x02040a: "black_concrete",
        0xaf6042: "terracotta",
        0xe0b8a5: "white_terracotta",
        0xbe571f: "orange_terracotta",
        0xa6546b: "magenta_terracotta",
        0x726c8c: "light_blue_terracotta",
        0xd28a11: "yellow_terracotta",
        0x61762c: "lime_terracotta",
        0xb94d4e: "pink_terracotta",
        0x3c271f: "gray_terracotta",
        0x916a60: "light_gray_terracotta",
        0x535c5c: "cyan_terracotta",
        0x864456: "purple_terracotta",
        0x513d5e: "blue_terracotta",
        0x54301e: "brown_terracotta",
        0x4b5526: "green_terracotta",
        0xa33728: "red_terracotta",
        0x250e05: "black_terracotta",
        0x91648b: "purpur_block",
        0x0e0d0d: "coal_block",
        0xbababa: "iron_block",
        0xd3ae00: "gold_block",
        0x990000: "redstone_block",
        0x00b247: "emerald_block",
        0x083676: "lapis_block",
        0x00cbbf: "diamond_block",
        0x3a3538: "netherite_block",
        0xcdc7c0: "smooth_quartz",
        0x865cb5: "amethyst_block",
        0xb2573e: "copper_block",
        0xacb5b5: "snow_block",
        0x4a621c: "moss_block",
        0x795e4e: "dripstone_block",
        0xada98e: "bone_block",
        0x92725a: "raw_iron_block",
        0x9d5941: "raw_copper_block",
        0xc18a00: "raw_gold_block",
        0x845d3f: "brown_mushroom_block",
        0xb91420: "red_mushroom_block",
        0x6b0000: "nether_wart_block",
        0x006565: "warped_wart_block",
        0x333727: "dried_kelp_block"

        # Grass can die and therefore change color. So leave it out.
        # 0x346231: "grass_block",

        # The following blocks are vibrant, but die and turn gray within a few
        # seconds when not touching water:
        # 0x1d4db8: "tube_coral_block",
        # 0xbe4b8a: "brain_coral_block",
        # 0x9b008f: "bubble_coral_block",
        # 0x9b0521: "fire_coral_block",
        # 0xb8a702: "horn_coral_block",
      }),
    PaletteLayer(
      min_alpha=63,
      blocks={
        0xa9a9a9: "white_stained_glass",
        0x7c7c7c: "light_gray_stained_glass",
        0x656565: "gray_stained_glass",
        0x525252: "black_stained_glass",
        0x71655b: "brown_stained_glass",
        0x895a5b: "red_stained_glass",
        0xa17757: "orange_stained_glass",
        0x9e9e52: "yellow_stained_glass",
        0x719748: "lime_stained_glass",
        0x6d7959: "green_stained_glass",
        0x607983: "cyan_stained_glass",
        0x69819a: "light_blue_stained_glass",
        0x5a658e: "blue_stained_glass",
        0x7d5f8e: "purple_stained_glass",
        0x92639e: "magenta_stained_glass",
        0xac7787: "pink_stained_glass"
      }),
    PaletteLayer(
      min_alpha=31,
      blocks={
        0x000000: "glass"
      })
  ])


def nearest_color_block(palette: Palette, color: int) -> str:
  """Returns block with the nearest color in the given palette at the highest matching alpha layer.

  Args:
    palette: block palette mapping an int RGBA value to a block type
    color: 32-bit ARGB value with 8 bits alpha, 8 bits red, 8 bits green, 8 bits blue
  """
  alpha = color >> 24
  rgb = color & 0xffffff
  for layer in palette.layers:
    if alpha >= layer.min_alpha:
      min_color_distance = sys.maxsize
      closest_color = 0
      for block_color in layer.blocks.keys():
        d = color_distance_squared(block_color, rgb)
        if d < min_color_distance:
          min_color_distance = d
          closest_color = block_color
      return layer.blocks[closest_color]
  return None


def color_int24_to_components(color: int):
  """Convert 24-bit integer color to individual 8-bit components. (ignore alpha)

  Args:
    color: 24-bit int with high 8 bits red, middle 8 bits green, low 8 bits blue.

  Returns:
    tuple of three 8-bit values: (red, gree, blue)
  """
  red = (color >> 16) & 0xff
  green = (color >> 8) & 0xff
  blue = color & 0xff
  return (red, green, blue)


def color_distance_squared(color1: int, color2: int):
  """Compute the square of the color distance between two 24-bit colors.

  Colors are represented as 24-bit ints: high 8 bits red, middle 8 bits green, low 8 bits blue.
  """
  r1, g1, b1 = color_int24_to_components(color1)
  r2, g2, b2 = color_int24_to_components(color2)
  return (r1 - r2) ** 2 + (g1 - g2) ** 2 + (b1 - b2) ** 2


def add_coords(p1: BlockPos, p2: BlockPos) -> BlockPos:
  return (p1[0] + p2[0], p1[1] + p2[1], p1[2] + p2[2])


USE_BLOCKPACKER = True

def fill(blockpacker: BlockPacker, pos1: BlockPos, pos2: BlockPos, block: str):
  if USE_BLOCKPACKER:
    blockpacker.fill(pos1, pos2, block)
  else:
    print(f"/fill {pos1[0]} {pos1[1]} {pos1[2]} {pos2[0]} {pos2[1]} {pos2[2]} {block}")


def setblock(blockpacker: BlockPacker, pos: BlockPos, block: str):
  if USE_BLOCKPACKER:
    blockpacker.setblock(pos, block)
  else:
    print(f"/setblock {pos[0]} {pos[1]} {pos[2]} {block}")


def write_world(blockpacker: BlockPacker):
  if USE_BLOCKPACKER:
    blockpacker.pack().write_world()


ImagePos = Tuple[int, int, int]

@dataclass
class Orientation:
  """Mapping from image coordinates (x, y, depth) to world coordinates (x, y, z).

  `*_multiplier` fields are multipliers: -1, 0, or 1
  `*_dimension` fields are 0 for image x, 1 for image y, 2 for image depth
  """
  x_multiplier: int
  x_dimension: int
  y_multiplier: int
  y_dimension: int
  z_multiplier: int
  z_dimension: int

  def transform(self, pos: ImagePos) -> BlockPos:
    """Transforms (x, y, depth) in image coordinates to (x, y, z) world coordinates."""
    return (
        self.x_multiplier * pos[self.x_dimension],
        self.y_multiplier * pos[self.y_dimension],
        self.z_multiplier * pos[self.z_dimension])


ORIENTATION_2D_RE = re.compile("^([-+]?)([xyz]),([-+]?)([xyz])$")

def parse_2d_orientation(orientation_str: str) -> Orientation:
  """Parses 2D image orientation relative to world dimensions into an Orientation object.

  Args:
    orientation_str: comma-delimited str of world dimensions with optional sign, e.g. "x,-y"
      where image x maps to world x and image y maps to world -y
  """
  m = ORIENTATION_2D_RE.match(orientation_str)
  if not m:
    return None

  dimension_names = [m.group(2), m.group(4)]
  input_multipliers = [
      -1 if m.group(1) == "-" else 1,
      -1 if m.group(3) == "-" else 1]

  return parse_orientation(dimension_names, input_multipliers)


ORIENTATION_3D_RE = re.compile("^([-+]?)([xyz]),([-+]?)([xyz]),([-+]?)([xyz])$")

def parse_3d_orientation(orientation_str: str) -> Orientation:
  """Parses 3D image orientation relative to world dimensions into an Orientation object.

  Args:
    orientation_str: comma-delimited str of world dimensions with optional sign, e.g. "x,-z,y"
      where image x maps to world x, image y maps to world -z, and image depth maps to world y
  """
  m = ORIENTATION_3D_RE.match(orientation_str)
  if not m:
    return None

  dimension_names = [m.group(2), m.group(4), m.group(6)]
  input_multipliers = [
      -1 if m.group(1) == "-" else 1,
      -1 if m.group(3) == "-" else 1,
      -1 if m.group(5) == "-" else 1]

  return parse_orientation(dimension_names, input_multipliers)


def parse_orientation(dimension_names: List[str], input_multipliers: List[int]) -> Orientation:
  """Parses orientation expressed in terms of how image dimensions map to world dimensions.

  Args:
    dimension_names: list of world dimensions (x, y, or z) orderd by image dimensions (x, y, depth)
    input_multipliers: list of ints (-1 or 1) relative to world dims, parallel to dimension_names
  """
  # Require that dimension_names are unique, i.e. no dimensions are repeated.
  if len(dimension_names) > len(set(dimension_names)):
    return None

  # Returns (dim_multiplier, dim_index) where dim_index is the index of this world
  # dimension in image space and dim_multiplier is -1 (inverted), 0 (ignored), or
  # 1 (aligned) with the corresponding image dimension.
  def get_dimension_transform(world_dim_name: str) -> Tuple[int, int]:
    if world_dim_name in dimension_names:
      dim_index = dimension_names.index(world_dim_name)
      dim_multiplier = input_multipliers[dim_index]
    else:
      dim_index = -1
      dim_multiplier = 0
    return (dim_multiplier, dim_index)

  x_multiplier, world_x = get_dimension_transform("x")
  y_multiplier, world_y = get_dimension_transform("y")
  z_multiplier, world_z = get_dimension_transform("z")

  return Orientation(
      x_multiplier, world_x,
      y_multiplier, world_y,
      z_multiplier, world_z)


class ImageToBlocksException(Exception):
  def __init__(self, message: str):
    super().__init__(message)
    self.message = message


def render_image(
    offset: BlockPos, orientation: Orientation, image_filename: str, palette: Palette,
    depth_filename: str = None, depth_scale: float = 1):
  """Renders an image with optional depth map to blocks in world space.

  Args:
    offset: (x, y, z) offset in world space corresponding to image (0, 0) depth 0
    orientation: mapping from image space (x, y, depth) to world space (x, y, z)
    image_filename: filename of RGB (optional alpha) image
    palette: block palette mapping color/alpha values to a block type
    depth_filename: optional filename of grayscale depth image (optional alpha ignored)
    depth_scale: optional factor by which to divide depth image values
  """
  color_reader = png.Reader(file=open(image_filename, "rb"))
  image_width, image_height, image_rows, image_info = color_reader.read()

  # TODO(maxuser): Check that the chunks intersecting `offset` to (image_width, image_height)
  # when transformed by `orientation` are loaded. Note that if depth map is present and oriented
  # along world x or z axes, the maximum distance along that axis is 255 / depth_scale.

  if image_info.get("greyscale") == True:
    raise ImageToBlocksException(f"Error: expected RGB image but got greyscale in {image_filename}")

  image_planes = image_info.get("planes")
  if image_planes not in (3, 4):
    raise ImageToBlocksException(
        f"Error: expected image to have 3 (RGB) or 4 (RGBA) planes but got {image_planes} "
        f"in {image_filename}")

  image_bitdepth = image_info.get("bitdepth")
  if image_bitdepth != 8:
    raise ImageToBlocksException(
        f"Error: expected image to have bitdepth of 8 but got {image_bitdepth} in {image_filename}")

  # TODO(maxuser): Support images with a color palette.

  echo(f"PNG image info: {image_info}")
  has_alpha = image_info["alpha"]

  depth_rows = None
  depth_has_alpha = False
  if depth_filename:
    depth_reader = png.Reader(file=open(depth_filename, 'rb'))
    depth_image_width, depth_image_height, depth_rows, depth_info = depth_reader.read()
    depth_has_alpha = depth_info["alpha"]
    echo(f"PNG depth info: {depth_info}")

    if depth_info.get("greyscale") == False:
      raise ImageToBlocksException(
          f"Error: expected depth image to be greyscale: {depth_filename}")

    depth_planes = depth_info.get("planes")
    if depth_planes != 1:
      raise ImageToBlocksException(
          f"Error: expected depth image to have 1 plane (greyscale) but got {depth_planes} "
          f"in {depth_filename}")

    depth_bitdepth = depth_info.get("bitdepth")
    if depth_bitdepth != 8:
      raise ImageToBlocksException(
          f"Error: expected depth image to have bitdepth of 8 but got {depth_bitdepth} in "
          f" {depth_filename}")


    if image_width != depth_image_width or image_height != depth_image_height:
      raise ImageToBlocksException(
          f"Error: color map and depth map differ in size: "
          f"{image_filename} is {image_width}x{image_height} and "
          f"{depth_filename} is {depth_image_width}x{depth_image_height}")
      return False

  blockpacker = BlockPacker()

  image_start_x = 0
  image_end_x = image_width - 1
  image_start_y = 0
  image_end_y = image_height - 1

  row_depths = None
  row_minus_1_depths = None
  row_minus_2_depths = None

  row_blocks = None
  row_minus_1_blocks = None

  depth_rows_iter = iter(depth_rows) if depth_rows else None

  init_row_depth = [0] * (image_end_x - image_start_x + 1)
  init_row_blocks = [0] * (image_end_x - image_start_x + 1)
  block_ids: Dict[str, int] = {None: 0} # block id zero is reserved
  blocks: List[str] = [None]

  def get_block_id(block_str: str) -> int:
    block_id = block_ids.setdefault(block_str, len(blocks))
    if block_id == len(blocks):
      blocks.append(block_str)
    return block_id

  for y, image_row in enumerate(image_rows):
    if y < image_start_y or y > image_end_y:
      continue

    is_final_row = (y == image_end_y)

    echo(f"Processing image row {y} of {image_height}...")

    row_minus_2_depths = row_minus_1_depths
    row_minus_1_depths = row_depths
    row_depths = array("B", init_row_depth)

    row_minus_1_blocks = row_blocks
    row_blocks = array("H", init_row_blocks)

    depth_row_iter = iter(next(depth_rows_iter)) if depth_rows_iter else None
    image_row_iter = iter(image_row)

    count = 0
    for x in range(image_start_x, image_end_x + 1):
      image_pixel = (
          (next(image_row_iter) << 16) |
          (next(image_row_iter) << 8) |
          next(image_row_iter))

      # TODO(maxuser): Maybe support alpha as inline depth within color image?
      if has_alpha:
        alpha = next(image_row_iter)
      else:
        alpha = 0xff
      image_pixel = image_pixel | (alpha << 24)

      if depth_row_iter:
        depth = int(next(depth_row_iter) / depth_scale)
        if depth_has_alpha:
          ignore_alpha = next(depth_row_iter)
      else:
        depth = 0

      if x < image_start_x or x > image_end_x:
        continue

      row_depths[x - image_start_x] = depth
      row_blocks[x - image_start_x] = get_block_id(nearest_color_block(palette, image_pixel))

    for x in range(0, image_end_x - image_start_x + 1):
      depth = row_minus_1_depths[x] if row_minus_1_depths else row_depths[x]
      neighbor_min_depth = min(
          row_depths[x],
          row_minus_1_depths[x - 1] if row_minus_1_depths and x > 0 else sys.maxsize,
          (row_minus_1_depths[x + 1]
              if row_minus_1_depths and x + 1 < len(row_minus_1_depths)
              else sys.maxsize),
          row_minus_2_depths[x] if row_minus_2_depths else sys.maxsize)

      if row_minus_1_blocks:
        block = blocks[row_minus_1_blocks[x]]
        if block is not None:
          render_image_pixel(
              block, x, y - image_start_y - 1, depth,
              neighbor_min_depth, offset, orientation, blockpacker)

      # Special-case the final row.
      if is_final_row:
        depth = row_depths[x]
        neighbor_min_depth = min(
            row_depths[x - 1] if x > 0 else sys.maxsize,
            (row_depths[x + 1]
                if x + 1 < len(row_depths)
                else sys.maxsize),
            row_minus_1_depths[x] if row_minus_1_depths else sys.maxsize)

        block = blocks[row_blocks[x]]
        if block is not None:
          render_image_pixel(
              block, x, y - image_start_y, depth,
              neighbor_min_depth, offset, orientation, blockpacker)

  write_world(blockpacker)


def render_image_pixel(
    block: str, x: int, y: int, depth: int, neighbor_min_depth: int,
    offset: BlockPos, orientation: Orientation, blockpacker: BlockPacker):
  """Renders an image pixel as a block in world space.

  Args:
    block: block string with optional attributes, e.g. "grass_block[snowy=true]"
    x, y, depth: image coordinate
    neighbor_min_depth: min depth value of neighboring pixels
    offset: (x, y, z) offset in world space corresponding to image (0, 0) depth 0
    orientation: mapping from image space (x, y, depth) to world space (x, y, z)
    blockpacker: BlockPacker to add blocks to
  """
  if depth - neighbor_min_depth > 1:
    fill(blockpacker, add_coords(orientation.transform((x, y, depth)), offset),
        add_coords(orientation.transform((x, y, neighbor_min_depth)), offset), block)
  else:
    setblock(
        blockpacker, add_coords(orientation.transform((x, y, depth)), offset), block)


def is_positive_float(s: str) -> bool:
  """Returns True if `s` represents a positive float."""
  try:
    return float(s) > 0
  except ValueError:
    return False


def load_image_spec(json_filename: str) -> ImageSpec:
  """Loads a block palette from JSON file."""
  image_spec = ImageSpec()
  data = json.load(open(json_filename))

  image_spec.orientation = data.get("orientation")
  image_spec.color_map = data.get("color_map")
  image_spec.depth_map = data.get("depth_map")

  def get_path_relative_to_json_file(filename: str):
    if filename is None or os.path.isabs(filename):
      return None
    return os.path.join(os.path.dirname(json_filename), filename)

  image_spec.color_map = get_path_relative_to_json_file(image_spec.color_map)
  image_spec.depth_map = get_path_relative_to_json_file(image_spec.depth_map)

  raw_depth_scale = data.get("depth_scale")
  if raw_depth_scale is not None:
    image_spec.depth_scale = float(raw_depth_scale)

  raw_palette = data.get("palette")
  if raw_palette:
    palette = Palette([])
    for layer in raw_palette:
      min_alpha = layer["min_alpha"]
      raw_blocks = layer["blocks"]
      blocks = {int(k.lstrip("#"), 16):v for k, v in raw_blocks.items()}
      palette.layers.append(PaletteLayer(min_alpha, blocks))
    image_spec.palette = palette

  return image_spec


def main(argv) -> bool:
  world_start_x = int(argv[1])
  world_start_y = int(argv[2])
  world_start_z = int(argv[3])

  # Parse the optional params remaining after the first 5 fixed params.
  remaining_params: List[str] = argv[4:]
  orientation_param: str = None
  palette: Palette = None
  json_filename: str = None
  image_filename: str = None
  depth_filename: str = None
  depth_scale: float = None
  image_spec: ImageSpec = None

  DEPTH_SCALE_PARAM_RE = re.compile(r"dscale=([0-9.]+)")

  def echo_unexpected_orientation():
    echo(
        f"Error: orientation param `{orientation_param}` cannot appear before "
        f"`{remaining_params[0]}`")
    return False

  while remaining_params:
    if remaining_params[0].lower().endswith(".png"):
      if orientation_param is not None:
        return echo_unexpected_orientation()
      if image_filename is None:
        image_filename = remaining_params.pop(0)
        if not os.path.isabs(image_filename):
          image_filename = os.path.join("minescript", image_filename)
      elif depth_filename is None:
        depth_filename = remaining_params.pop(0)
        if depth_filename and not os.path.isabs(depth_filename):
          depth_filename = os.path.join("minescript", depth_filename)
      else:
        echo(f"Error: too many PNG files, exceeds max of two: `{remaining_params[0]}`")
        return False
    elif remaining_params[0].startswith("dscale="):
      param = remaining_params.pop(0)
      m = DEPTH_SCALE_PARAM_RE.match(param)
      if m and is_positive_float(m.group(1)):
        depth_scale = float(m.group(1))
      else:
        echo(
            f"Error: expected dscale param to be a positive number but got `{param}`")
        return False
    elif remaining_params[0].lower().endswith(".json") and json_filename is None:
      if orientation_param is not None:
        return echo_unexpected_orientation()
      json_filename = remaining_params.pop(0)
      if not os.path.isabs(json_filename):
        json_filename = os.path.join("minescript", json_filename)
      image_spec = load_image_spec(json_filename)
    elif orientation_param is None and (
        ORIENTATION_2D_RE.match(remaining_params[0]) or
            ORIENTATION_3D_RE.match(remaining_params[0])):
      orientation_param = remaining_params.pop(0)
    else:
      echo(f"Error: unrecognized param `{remaining_params[0]}`")
      return False

  # If an ImageSpec was loaded from a JSON file, propagate its fields to params
  # not explicitly passed via argv.
  if image_spec is not None:
    if image_spec.orientation is not None and orientation_param is None:
      orientation_param = image_spec.orientation
    if image_spec.color_map is not None and image_filename is None:
      image_filename = image_spec.color_map
    if image_spec.depth_map is not None and depth_filename is None:
      depth_filename = image_spec.depth_map
    if image_spec.depth_scale is not None and depth_scale is None:
      depth_scale = image_spec.depth_scale
    if image_spec.depth_scale is not None:
      palette = image_spec.palette

  if orientation_param is None:
    echo(
        f"Error: missing orientation: comma-separated coordinates in "
        f"terms of x, y, and z, for example `x,-z` for a 2D image or `x,-z,y` "
        f"for an image with a depth map")
    return False

  orientation: Orientation = None
  if depth_filename:
    orientation = parse_3d_orientation(orientation_param)
  else:
    orientation = parse_2d_orientation(orientation_param)
  if orientation is None:
    echo(
        f"Error: expected orientation to be comma-separated coordinates in "
        f"terms of x, y, and z, for example `x,-z` for a 2D image or `x,-z,y` "
        f"for an image with a depth map, but got: {orientation_param}")
    return False


  try:
    render_image(
        (world_start_x, world_start_y, world_start_z), orientation, image_filename,
        palette or default_palette, depth_filename, depth_scale or 1)
    return True
  except ImageToBlocksException as e:
    echo(e.message)
    return False


if __name__  == '__main__':
  ok: bool = main(sys.argv)
  sys.exit(0 if ok else 1)
