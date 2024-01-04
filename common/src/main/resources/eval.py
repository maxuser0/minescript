# SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

r"""eval v4.0 distributed via Minescript jar file

Usage:
  \eval <pythonCode> [<line2> [<line3> ...]]

Executes <pythonCode> (and optional subsequent lines
<line2>, <line3>, etc) as either a Python expression (code that
can appear on the right-hand side of an assignment, in which
case the value is echoed to the chat screen) or Python
statements (e.g. a `for` loop).

Functions from minescript.py are available automatically without
qualification.

Examples:
  Print information about nearby entities to the chat screen:
  \eval "entities()"

  Print the names of nearby entities to the chat screen:
  \eval "for e in entities(): echo(e['name'])"

  Import `time` module, sleep 3 seconds, and take a screenshot:
  \eval "import time" "time.sleep(3)" "screenshot()"

"""

# `from ... import *` is normally considered poor form because of namespace
# pollution.  But it's desirable in this case because it allows single-line
# Python code that's entered in the Minecraft chat screen to omit the module
# prefix for brevity. And brevity is important for this use case.
from minescript import *
from typing import Any
import builtins
import sys

def run(python_code: str) -> None:
  """Executes python_code as an expression or statements.

  Args:
    python_code: Python expression or statements (newline-delimited)
  """
  # Try to evaluate as an expression.
  try:
    print(builtins.eval(python_code), file=sys.stderr)
    return
  except SyntaxError:
    pass

  # Fall back to executing as statements.
  builtins.exec(python_code)


if __name__ == "__main__":
  if len(sys.argv) < 2:
    print(
        f"eval.py: Expected at least 1 parameter, instead got {len(sys.argv) - 1}: {sys.argv[1:]}",
        file=sys.stderr)
    print(r"Usage: \eval <pythonCode> [<line2> [<line3> ...]]", file=sys.stderr)
    sys.exit(1)

  run("\n".join(sys.argv[1:]))
