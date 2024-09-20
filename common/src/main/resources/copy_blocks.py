# SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

r"""copy_blocks v4.0 distributed via Minescript jar file

Requires:
  minescript v3.0

Usage: \copy X1 Y1 Z1 X2 Y2 Z2 [LABEL] [no_limit]

Copies blocks within the rectangular box from (X1, Y1, Z1) to
(X2, Y2, Z2), similar to the coordinates passed to the /fill
command. LABEL is optional, allowing a set of blocks to be
named.

By default, attempts to copy a region covering more than
1600 chunks are disallowed. This limit can be relaxed by
passing `no_limit`.
"""

import minescript
import os
import sys

from minescript import echo, BlockPack
from minescript_runtime import JavaException

def main(args):
  if len(args) not in (6, 7, 8):
    echo(
        "Error: copy command requires 6 params of type integer "
        "(plus optional params for label and `no_limit`)")
    return

  safety_limit = True
  if "no_limit" in args:
    args.remove("no_limit")
    safety_limit = False

  x1 = int(args[0])
  y1 = int(args[1])
  z1 = int(args[2])

  x2 = int(args[3])
  y2 = int(args[4])
  z2 = int(args[5])

  if len(args) == 6:
    label = "__default__"
  else:
    label = args[6].replace("/", "_").replace("\\", "_").replace(" ", "_")

  blockpacks_dir = os.path.join("minescript", "blockpacks")
  if not os.path.exists(blockpacks_dir):
    os.makedirs(blockpacks_dir)

  copy_file = os.path.join(blockpacks_dir, label + ".zip")

  try:
    blockpack = BlockPack.read_world(
        (x1, y1, z1), (x2, y2, z2), offset=(-x1, -y1, -z1),
        comments={"name": label, "source command": f"copy {' '.join(sys.argv[1:])}"},
        safety_limit=safety_limit)
  except JavaException as e:
    echo(e.message)
    return
  blockpack.write_file(copy_file, relative_to_cwd=True)
  file_size_str = "{:,}".format(os.stat(copy_file).st_size)
  echo(
      f"Copied volume {abs(x1 - x2) + 1} * {abs(y1 - y2) + 1} * {abs(z1 - z2) + 1} to "
      f"{copy_file} ({file_size_str} bytes).")
  del blockpack


if __name__ == "__main__":
  main(sys.argv[1:])
