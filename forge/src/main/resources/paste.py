# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

r"""paste v2.2 distributed via Minescript jar file

Requires:
  minescript v2.1

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

from minescript import echo, getblocklist

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

  copy_command_re = re.compile("# copy ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+) ([-0-9]+)")

  paste_file = open(os.path.join("minescript", "copies", label + ".txt"))
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
        sample_blocks_by_chunk = []
        for xchunk in range(x, x + dx, 16):
          for zchunk in range(z, z + dz, 16):
            sample_blocks_by_chunk.append((xchunk, 0, zchunk))
        num_chunks = len(sample_blocks_by_chunk)
        if safety_limit and num_chunks > 1600:
          echo(
            f"`paste` command exceeded soft limit of 1600 chunks "
            f"(region covers {num_chunks} chunks; override this safety check with `no_limit`).")
          return
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
          return
        echo(
            f"All {num_chunks} chunk(s) loaded within the requested `paste` volume; "
            "pasting blocks...")
      continue

    fields = line.split(" ", 4)
    if fields[0] != "/setblock":
      echo("Error: paste works only with setblock commands, but got the following instead:\n")
      echo(line)
      return

    # Apply coordinate offsets:
    fields[1] = str(int(fields[1]) + x)
    fields[2] = str(int(fields[2]) + y)
    fields[3] = str(int(fields[3]) + z)

    minescript.exec(" ".join(fields))

if __name__ == "__main__":
  main(sys.argv[1:])
