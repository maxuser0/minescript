#!/usr/bin/python3
# SPDX-FileCopyrightText: © 2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

"""Utility for dumping JSON representation of Python function AST."""

import ast
import json
import sys

def ast_to_dict(
    node: ast.AST,
    skip_attributes={"end_lineno", "end_col_offset", "kind", "ctx"}):
  """Recursively converts an AST node to a dictionary

  Args:
    node: AST node
    skip_attributes: names of attributes to skip when generating output dict

  Returns:
    dict representing the given AST node
  """
  if skip_attributes is None:
    skip_attributes = set()  # Initialize as an empty set if not provided

  if isinstance(node, ast.AST):
    d = {'type': node.__class__.__name__}
    for field, value in ast.iter_fields(node):
      if field not in skip_attributes:  # Skip attribute if in the list
        d[field] = ast_to_dict(value, skip_attributes)
    for attr in node._attributes:
      if attr not in skip_attributes:  # Skip attribute if in the list
        d[attr] = ast_to_dict(getattr(node, attr), skip_attributes)
    if d['type'] == 'Constant':
      d['typename'] = type(d['value']).__name__
    return d
  elif isinstance(node, list):
    return [ast_to_dict(x, skip_attributes) for x in node]
  elif isinstance(node, (str, int, float, bool, type(None))):
    return node
  else:
    return str(node)

USAGE = """Usage:
    dump_json_ast.py <some_code.py
    dump_json_ast.py some_code.py
    dump_json_ast.py -s some_code.py  # dump Python source as JSON strings

Example:
# Convert Python code to AST in JSON format, then pipe JSON to the interpreter:
$ app/src/main/resources/dump_json_ast.py <<EOF |./gradlew run
def foo(x):
  print(f"This is a {x}.")
foo("test")
EOF

output:
This is a test.
"""

def main(argv):
  dump_source = False
  if "-s" in argv:
    dump_source = True
    argv.remove("-s")

  if len(argv) == 0:
    code = ""
    try:
      for line in sys.stdin.readlines():
        if line.strip() == ".":
          break
        code += line
    except EOFError:
      pass
  elif len(argv) == 1:
    with open(argv[0], "r") as file:
      code = file.read()
  else:
    print(USAGE, file=sys.stderr)
    return

  tree = ast.parse(code)
  ast_dict = ast_to_dict(tree)
  if dump_source:
    # Update the dicts in this roundabout order to ensure pysrc appears first in the output.
    warning = [
      "# WARNING: Editing this Python code has no effect.",
      "# To modify the code, re-run dump_json_ast.py to regenerate the JSON below."
    ]
    new_ast_dict = { "pysrc": warning + code.split("\n") }
    new_ast_dict.update(ast_dict)
    ast_dict = new_ast_dict
  print(json.dumps(ast_dict, indent=2))

if __name__ == "__main__":
  main(sys.argv[1:])

