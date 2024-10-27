#!/usr/bin/python3
# SPDX-FileCopyrightText: © 2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

"""Utility for dumping JSON representation of Python function AST."""

import inspect
import ast
import json
import math
import sys

def ast_to_dict(
    node: ast.AST,
    skip_attributes={"lineno", "col_offset", "end_lineno", "end_col_offset"}):
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
    return d
  elif isinstance(node, list):
    return [ast_to_dict(x, skip_attributes) for x in node]
  elif isinstance(node, (str, int, float, bool, type(None))):
    return node
  else:
    return str(node)

def distance_vec3(p1, p2):
  dx = p1[0] - p2[0]
  dy = p1[1] - p2[1]
  dz = p1[2] - p2[2]
  d_squared = dx * dx + dy * dy + dz * dz
  return math.sqrt(d_squared)

def distance_scalar2(x1, y1, x2, y2):
  dx = x1 - x2
  dy = y1 - y2
  d_squared = dx * dx + dy * dy
  return math.sqrt(d_squared)

def times_two(x):
  y = x * 2
  return y

def populate_array(array, index, value):
  array[index] = value
  return array

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
  elif len(argv) == 1:
    func = globals()[argv[0]]
    tree = ast.parse(inspect.getsource(func))
    print(json.dumps(ast_to_dict(tree), indent=2))
  else:
    print(USAGE, file=sys.stderr)

if __name__ == "__main__":
  main(sys.argv[1:])

