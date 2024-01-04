# SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

r"""paste v4.0 distributed via Minescript jar file

Requires:
  minescript v3.0

Usage: \paste X Y Z [LABEL]

Pastes blocks at location (X, Y, Z) that were previously copied
via \copy.  When the optional param LABEL is given, blocks are
pasted from the most recent copy command with the same
LABEL given, otherwise blocks are pasted from the most
recent copy command with no label given.
"""

import minescript
import os
import re
import sys

from minescript import echo, getblocklist, BlockPack

def is_eligible_for_paste(x, z, dx, dz, safety_limit) -> bool:
  sample_blocks_by_chunk = []
  for xchunk in range(x, x + dx, 16):
    for zchunk in range(z, z + dz, 16):
      sample_blocks_by_chunk.append((xchunk, 0, zchunk))
  num_chunks = len(sample_blocks_by_chunk)
  if safety_limit and num_chunks > 1600:
    echo(
      f"`paste` command exceeded soft limit of 1600 chunks "
      f"(region covers {num_chunks} chunks; override this safety check with `no_limit`).")
    return False
  echo(f"Checking {num_chunks} chunk(s) for load status...")
  blocks = getblocklist(sample_blocks_by_chunk)
  num_unloaded_blocks = 0
  for block in blocks:
    if not block or block == "minecraft:void_air":
      num_unloaded_blocks += 1
  if num_unloaded_blocks > 0:
    echo(
        f"{num_unloaded_blocks} of {num_chunks} chunks are not loaded "
        "within the requested `paste` volume. Cancelling paste.");
    return False
  echo(
      f"All {num_chunks} chunk(s) loaded within the requested `paste` volume; "
      "pasting blocks...")

  return True


def main(args):
  if len(args) not in (3, 4, 5):
    echo(
        "Error: paste command requires 3 params of type integer "
        "(plus optional params for label and `no_limit`)")
    return

  safety_limit = True
  if "no_limit" in args:
    args.remove("no_limit")
    safety_limit = False

  x = int(args[0])
  y = int(args[1])
  z = int(args[2])

  if len(args) == 3:
    label = "__default__"
  else:
    label = args[3].replace("/", "_").replace("\\", "_").replace(" ", "_")

  # BlockPacks (stored in .zip files) take precedence over legacy .txt files containing setblock
  # commands.
  blockpack_filename = os.path.join("minescript", "blockpacks", label + ".zip")
  legacy_txt_filename = os.path.join("minescript", "copies", label + ".txt")
  if os.path.isfile(blockpack_filename):
    blockpack = BlockPack.read_file(blockpack_filename, relative_to_cwd=True)
    min_block, max_block = blockpack.block_bounds()
    dx = max_block[0] - min_block[0]
    dz = max_block[2] - min_block[2]
    if not is_eligible_for_paste(x, z, dx, dz, safety_limit):
      return
    blockpack.write_world(offset=(x, y, z))
    del blockpack
  elif os.path.isfile(legacy_txt_filename):
    paste_file = open(legacy_txt_filename)
    copy_command_re = re.compile(
        "# copy ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+)")
    for line in paste_file.readlines():
      line = line.rstrip()
      if line.startswith("#"):
        m = copy_command_re.match(line)
        if m:
          x1 = int(m.group(1))
          y1 = int(m.group(2))
          z1 = int(m.group(3))
          x2 = int(m.group(4))
          y2 = int(m.group(5))
          z2 = int(m.group(6))
          dx = max(x1, x2) - min(x1, x2)
          dz = max(z1, z2) - min(z1, z2)
          if not is_eligible_for_paste(x, z, dx, dz, safety_limit):
            return
        continue

      fields = line.split(" ")
      if fields[0] == "/setblock":
        # Apply coordinate offsets:
        fields[1] = str(int(fields[1]) + x)
        fields[2] = str(int(fields[2]) + y)
        fields[3] = str(int(fields[3]) + z)
        minescript.execute(" ".join(fields))
      elif fields[0] == "/fill":
        # Apply coordinate offsets:
        fields[1] = str(int(fields[1]) + x)
        fields[2] = str(int(fields[2]) + y)
        fields[3] = str(int(fields[3]) + z)
        fields[4] = str(int(fields[4]) + x)
        fields[5] = str(int(fields[5]) + y)
        fields[6] = str(int(fields[6]) + z)
        minescript.execute(" ".join(fields))
      else:
        echo(
            "Error: paste works only with setblock and fill commands, "
            "but got the following instead:\n")
        echo(line)
        return
  else:
    echo(f"Error: blockpack file for `{label}` not found at {blockpack_filename}")

if __name__ == "__main__":
  main(sys.argv[1:])
