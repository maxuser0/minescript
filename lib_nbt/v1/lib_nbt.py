#!/usr/bin/python3

r"""lib_nbt v1 distributed via minescript.net

Usage:
  standalone: `\lib_nbt <nbtString>`
  library: `lib_nbt.parse_snbt(nbt_string) -> dict`
"""

import re
import shlex
import sys

from dataclasses import dataclass
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

def _nbt_string_to_tokens(nbt_str: str):
  "Tokenizes an NBT string into a list of string tokens."
  lex = shlex.shlex(nbt_str, posix=True)
  lex.escapedquotes = lex.quotes  # escape single quotes, too
  lex.wordchars += ".;-" # parse floating-point numbers as a single token
  return lex

class Unassigned:
  pass

unassigned = Unassigned()

@dataclass
class DictItem:
  key: Any


bool_re = re.compile("(true|false)$")
int_re = re.compile(r"(-?[0-9]+)[bBsSlL]?$")
float_re = re.compile(r"(-?[0-9]+.?[0-9]*)[fFdD]$")
array_re = re.compile(r"[BIL];(.*)$")

def parse_snbt(nbt_str: str) -> Dict:
  context = [unassigned] # elements are the types dict or list
  processed_tokens = ""

  def parse_value(value: Any) -> Any:
    if type(value) is str:
      m = bool_re.match(value)
      if m:
        return m.group(1) == "true"
      m = int_re.match(value)
      if m:
        return int(m.group(1))
      m = float_re.match(value)
      if m:
        return float(m.group(1))
    return value

  def process_value(value: Any):
    nonlocal context
    value = parse_value(value)

    c = context[-1]
    if c is unassigned:
      context[-1] = value
    elif type(c) is list:
      if len(c) == 0 and type(value) is str:
        m = array_re.match(value)
        if m:
          value = parse_value(m.group(1))
      c.append(value)
    elif type(c) is DictItem:
      context.pop()
      c2 = context[-1]
      if type(c2) is not dict:
        raise Exception(f"Expected context to be a dict but got `{c2}`")
      c2[c.key] = value
    elif type(c) is dict:
      context.append(DictItem(key=value))

    value_type = type(value)
    if value_type in (list, dict):
      context.append(value)

  try:
    for token in _nbt_string_to_tokens(nbt_str):
      processed_tokens += token
      if token == "{":
        process_value({})
      elif token == "}":
        if len(context) > 1:
          context.pop()
      elif token == "[":
        process_value([])
      elif token == "]":
        if len(context) > 1:
          context.pop()
      elif token == ",":
        pass # TODO(maxuser): add error-checking so comma isn't optional
      elif token == ":":
        pass # TODO(maxuser): add error-checking so colon isn't optional
      else:
        process_value(token)
  except Exception as e:
    raise Exception(e.args[0], context, processed_tokens)

  return None if context[0] is unassigned else context[0]

if __name__ == "__main__":
  print(parse_snbt(sys.argv[1]))
