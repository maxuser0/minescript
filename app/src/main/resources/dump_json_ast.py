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
    dump_json_ast.py [func_name]
    cat some_code.py |dump_json_ast.py"""

def main(argv):
  if len(argv) == 0:
    code = ""
    try:
      for line in sys.stdin.readlines():
        if line.strip() == ".":
          break
        code += line
    except EOFError:
      pass
    tree = ast.parse(code)
    print(json.dumps(ast_to_dict(tree), indent=2))
  else:
    print(USAGE, file=sys.stderr)

if __name__ == "__main__":
  main(sys.argv[1:])

