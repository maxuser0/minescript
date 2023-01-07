# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

r"""help v3.1 distributed via Minescript jar file

Usage: \help SCRIPT_NAME

Prints documentation string for the given script.
"""

import os
import sys

def ReadDocString(script_name):
  nlines = 0
  src = ""
  try:
    script = open(os.path.join("minescript", script_name))
  except FileNotFoundError as e:
    print(f'Script named "{script_name}" not found.', file=sys.stderr)
    return None
  docstr_start_quote = None
  while nlines < 100:
    nlines += 1
    line = script.readline()
    if not line:
      break
    if docstr_start_quote is None:
      if not line.strip() or line.startswith("#"):
        continue
      if line[:3] in ('"""', "'''"):
        docstr_start_quote = line[:3]
      elif line[:4] in ('r"""', "r'''"):
        docstr_start_quote = line[1:4]
      else:
        break
    src += line
    if line.rstrip().endswith(docstr_start_quote):
      return eval(src)
  return None

def run(argv):
  if len(argv) != 2:
    print(__doc__, file=sys.stderr)
    return 0 if len(argv) == 1 else 1

  script_name = argv[1] if argv[1].endswith(".py") else argv[1] + ".py"
  docstr = ReadDocString(script_name)
  if docstr is None:
    return 1
  print(docstr, file=sys.stderr)
  return 0

if __name__ == "__main__":
  sys.exit(run(sys.argv))
