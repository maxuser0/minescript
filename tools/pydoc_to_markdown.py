#!/usr/bin/python3

# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

"Tool for converting pydoc to Markdown."

import re
import sys
from dataclasses import dataclass
from enum import Enum
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

FUNCTION_RE = re.compile(r"^def ([a-zA-Z_0-9]+)(.*)")
DATACLASS_RE = re.compile(r"^@dataclass")
CLASS_RE = re.compile(r"^class ([a-zA-Z_0-9]+)")
CONDITIONAL_RE = re.compile(r"^if ")
DATACLASS_FIELD_RE = re.compile(r"^ +([a-zA-Z_0-9]+) *: *([a-zA-Z_0-9.]+)")
METHOD_RE = re.compile(r"^  def ([a-zA-Z_0-9]+)(.*)")
METHOD_DECORATION_RE = re.compile(r"^  (@(static|class)method)$")
GLOBAL_ASSIGNMENT_RE = re.compile(r"^([a-zA-Z_0-9]+)(: ([a-zA-Z_0-9.]+))? = ")
CLASS_ASSIGNMENT_RE = re.compile(r"^  ([a-zA-Z_0-9]+)(: ([a-zA-Z_0-9.]+))? = ")
BEGIN_TRIPLE_QUOTE = re.compile(r'^ *r?"""([^ ].*)')
END_TRIPLE_QUOTE = re.compile(r'(.*)"""$')
REQUIREMENT_LINE = re.compile(r'^  [a-zA-Z0-9_-]+ v.*')


class GlobalEntityType(Enum):
  CLASS = 1
  FUNCTION = 2
  ASSIGNMENT = 4

  def for_display(self):
    return self.name.lower()


class ClassMemberType(Enum):
  METHOD = 1
  ASSIGNMENT = 2

  def for_display(self):
    return self.name.lower()


class CodeEntity:
  pass


@dataclass
class ModuleEntity(CodeEntity):
  name: str = "__module__"
  kind: type = None
  decoration: str = None
  func_decl: str = None
  pydoc: str = None

  def fullname(self):
    return None


@dataclass
class GlobalEntity(CodeEntity):
  name: str
  kind: GlobalEntityType
  func_decl: str = None
  decoration: str = None
  is_dataclass: bool = False
  dataclass_fields: List[str] = None
  pydoc: str = None

  def fullname(self):
    return self.name


@dataclass
class ClassMember(CodeEntity):
  classname: str
  name: str
  kind: ClassMemberType
  func_decl: str = None
  decoration: str = None
  pydoc: str = None

  def fullname(self):
    return f"{self.classname}.{self.name}"


def escape_for_markdown(s: str):
  s = re.sub(r"__", r"\_\_", s)
  s = re.sub(r"\*", r"\*", s)
  return s


def rewrite_special_methods(func_decl: str) -> str:
  func_decl = func_decl.replace(".__init__(", "(")
  func_decl = re.sub(
      r"^([a-zA-Z0-9_]+)\.__del__\(self\)",
      lambda m: f"del {m.group(1).lower()}",
      func_decl)
  return func_decl

def linkify_func_decl(func_decl: str, anchors: Dict[str, str]) -> str:
  """Return rewritten decl with arg types linked to their definitions, if available in `anchors`."""
  func_decl= re.sub(r"'([a-zA-Z0-9_]+)'", r"\1", func_decl)

  # List of rewrites from (start, end) indices in pydoc to a replacement string.
  anchor_rewrites: List[Tuple[int, int, str]] = []
  for m in re.finditer(r"[:>] ([a-zA-Z0-9_]+)\b", func_decl):
    argtype = m.group(1)
    anchor = anchors.get(argtype)
    if anchor:
      start = m.start() + 2
      end = start + len(argtype)
      anchor_rewrites.append((start, end, f"[{argtype}](#{anchor})"))

  # Sort rewrites from highest to lowest index so the later rewrites do not
  # invalidate earlier indices.
  anchor_rewrites.sort(reverse=True)
  for start, end, replacement in anchor_rewrites:
    func_decl = func_decl[:start] + replacement + func_decl[end:]

  return func_decl


LEADING_SINGLE_UNDERSCORE_RE = re.compile("^_[^_]")

def process_pydoc(code_entity: CodeEntity, anchors: Dict[str, str]):
  if not code_entity:
    return

  pydoc = code_entity.pydoc or ""
  if not pydoc and not code_entity.is_dataclass:
    return

  if pydoc and "(__internal__)" in pydoc:
    return

  if code_entity and LEADING_SINGLE_UNDERSCORE_RE.match(code_entity.name):
    return

  is_class_member = type(code_entity) is ClassMember
  if is_class_member and LEADING_SINGLE_UNDERSCORE_RE.match(code_entity.classname):
    return

  # List of rewrites from (start, end) indices in pydoc to a replacement string.
  anchor_rewrites: List[Tuple[int, int, str]] = []
  for m in re.finditer(r"`[a-zA-Z0-9_.]+[^`]*`", pydoc):
    start = m.start()
    end_backtick = pydoc.find("`", start + 1)
    if end_backtick != -1:
      open_paren = pydoc.find("(", start + 1, end_backtick)
      if open_paren == -1:
        end = end_backtick
      else:
        end = open_paren
      symbol = pydoc[start+1:end]
      anchor = anchors.get(symbol)
      if not anchor:
        if is_class_member:
          symbol = f"{code_entity.classname}.{symbol}"
        elif type(code_entity) is GlobalEntity and code_entity.kind is GlobalEntityType.CLASS:
          symbol = f"{code_entity.name}.{symbol}"
        anchor = anchors.get(symbol)
      if anchor:
        anchor_rewrites.append((start, end_backtick, f"[{pydoc[start:end_backtick+1]}](#{anchor})"))

  # Sort rewrites from highest to lowest index so the later rewrites do not
  # invalidate earlier indices.
  anchor_rewrites.sort(reverse=True)
  for start, end, replacement in anchor_rewrites:
    pydoc = pydoc[:start] + replacement + pydoc[end + 1:]

  if type(code_entity) is ModuleEntity:
    # This is the module itself. Get the name from pydoc.
    module_name, version = pydoc.split()[0:2]
    if module_name in ("minescript", "java"):
      print(f"### {module_name} module")
    else:
      print(f"### {module_name} {version}")
    pydoc = re.sub(r"\nUsage: ([^\n]*)", r"\n*Usage:* `\1`", pydoc)
    pydoc = pydoc.replace("\nUsage:", "\n*Usage:*\n")
    pydoc_lines = pydoc.splitlines()[1:]
    is_requires_block = False
    for line in pydoc_lines:
      if line.lstrip().startswith("```"):
        line = line .lstrip()

      if line.strip().startswith("Example") and line.strip().endswith(":"):
        print(f"\n*{line.strip()}*\n")
      elif line == "Requires:":
        print("*Requires:*\n")
        is_requires_block = True
      elif is_requires_block:
        if REQUIREMENT_LINE.match(line):
          print(f"- `{line.strip()}`")
        else:
          is_requires_block = False
          print(line)
      else:
        print(line)
    print()

    for name, link in sorted(anchors.items(), key=lambda k: k[0].lower()):
      if "." in name or name.startswith("_"):
        continue
      print(f"- [`{name}`](#{link})")
    print()

    return

  prefix = ""
  name = code_entity.name
  if is_class_member:
    name = f"{code_entity.classname}.{name}"
    prefix = f"{code_entity.classname}."
  name = escape_for_markdown(name)

  heading = "####"
  if is_class_member:
    pydoc = pydoc.replace("\n  ", "\n")

  pydoc = (pydoc
      .replace("\n  ", "\n")
      .replace("\nArgs:", "\n*Args:*\n")
      .replace("\nReturns:\n  ", "\n*Returns:*\n\n- ")
      .replace("\nRaises:", "\n*Raises:*\n")
      .replace("\nExample:", "\n*Example:*\n")
      .replace("\nExamples:", "\n*Examples:*\n")
    )

  # Un-indent example text following the "Example" heading so that triple-backtick
  # code blocks convert properly to HTML.
  m = re.search(r"^\*Example(s)?:\*", pydoc, re.M)
  if m:
    example_text_start = m.start() + len(m.group(0))
    pydoc = pydoc[:example_text_start] + pydoc[example_text_start:].replace("\n  ", "\n")

  # Replace args with their backtick-quoted names.
  pydoc = re.sub("\n  ([a-z0-9_, ]+): ", r"\n- `\1`: ", pydoc)

  if type(code_entity) is ModuleEntity:
    print(f"module:\n{pydoc}")
  else:
    decoration = f"{code_entity.decoration} " if code_entity.decoration else ""
    if code_entity.func_decl:
      func_decl = escape_for_markdown(
          linkify_func_decl(rewrite_special_methods(prefix + code_entity.func_decl), anchors)
          .replace("(cls, ", "(")
          .replace("(cls)", "()")
          .replace("(self, ", "(")
          .replace("(self)", "()")
          .rstrip(":")
        )
      print(f"{heading} {name}\n*Usage:* <code>{decoration}{func_decl}</code>\n\n{pydoc}")
    else:
      print(f"{heading} {name}\n{pydoc}")
      if code_entity.kind is GlobalEntityType.CLASS and code_entity.is_dataclass and code_entity.dataclass_fields:
        print("```")
        for field in code_entity.dataclass_fields:
          print(f"  {field.strip()}")
        print("```")

  print()


def parse_code_entities() -> List[CodeEntity]:
  module_entity = ModuleEntity()
  global_entity = None
  is_dataclass = False
  class_member = None
  method_decoration = None
  pydoc = None
  global_conditional = False

  # List of pairs of code entity and its pydoc string.
  entities: List[CodeEntity] = []

  line_num = 0
  for line in sys.stdin.readlines():
    line_num += 1

    if pydoc is not None:
      m = END_TRIPLE_QUOTE.match(line)
      if m:
        pydoc += m.group(1)
        entity = class_member or global_entity or module_entity
        entity.pydoc = pydoc

        # Classes are added to the list of entities as soon as the `class` line is parsed,
        # so skip adding them here.
        # TODO(maxuser): Change non-class entities to be added upfront as well.
        if entity.kind is not GlobalEntityType.CLASS:
          entities.append(entity)

        pydoc = None
      else:
        pydoc += line
      continue
    
    # If line isn't strictly whitespace and doesn't start with spaces, then break out of global
    # conditional. This needs to be checked before CONDITIONAL_RE because the "if" line itself is
    # outdented.
    if line.strip() and line[:2] != "  ":
      global_conditional = False

    m = CONDITIONAL_RE.match(line)
    if m:
      global_conditional = True

    if global_conditional and len(line) > 2 and line[:2] == "  ":
      # Outdent the contents of the conditional.
      line = line[2:]

    m = BEGIN_TRIPLE_QUOTE.match(line)
    if m:
      pydoc = m.group(1)
      if pydoc.endswith('"""'):
        pydoc = pydoc[:-3]
        entity = class_member or global_entity or module_entity
        entity.pydoc = pydoc

        # Classes are added to the list of entities as soon as the `class` line is parsed,
        # so skip adding them here.
        # TODO(maxuser): Change non-class entities to be added upfront as well.
        if entity.kind is not GlobalEntityType.CLASS:
          entities.append(entity)

        pydoc = None
      else:
        pydoc += "\n"
      continue

    if class_member and class_member.func_decl:
      func = class_member
    elif global_entity and global_entity.func_decl:
      func = global_entity
    else:
      func = None

    if func and func.func_decl and not func.func_decl.endswith(":"):
      if not func.func_decl.endswith("("):
        func.func_decl += " "
      func.func_decl += line.strip()
      if func.func_decl.endswith(":"):
        continue

    m = FUNCTION_RE.match(line)
    if m:
      class_member = None
      global_entity = GlobalEntity(name=m.group(1), kind=GlobalEntityType.FUNCTION)
      func = global_entity
      func.func_decl = m.group(1) + m.group(2).replace(", _as_task=False", "")
      continue

    m = DATACLASS_RE.match(line)
    if m:
      is_dataclass = True

    m = CLASS_RE.match(line)
    if m:
      class_member = None
      global_entity = GlobalEntity(name=m.group(1), kind=GlobalEntityType.CLASS, is_dataclass=is_dataclass)
      entities.append(global_entity)
      is_dataclass = False

    m = METHOD_DECORATION_RE.match(line)
    if m:
      method_decoration = m.group(1)
      continue

    m = METHOD_RE.match(line)
    if m:
      if global_entity is None or global_entity.kind != GlobalEntityType.CLASS:
        if global_entity.kind != GlobalEntityType.FUNCTION:
          print(f"ERROR: encountered method `{m.group(1)}` while not in {global_entity.kind}")
        continue
      class_member = ClassMember(
          classname=global_entity.name, name=m.group(1), kind=ClassMemberType.METHOD,
          decoration=method_decoration)
      func = class_member
      func.func_decl = m.group(1) + m.group(2)
      method_decoration = None
      continue

    m = GLOBAL_ASSIGNMENT_RE.match(line)
    if m:
      class_member = None
      global_entity = GlobalEntity(name=m.group(1), kind=GlobalEntityType.ASSIGNMENT)

    if global_entity is not None and global_entity.kind == GlobalEntityType.CLASS:
      if global_entity.is_dataclass:
        m = DATACLASS_FIELD_RE.match(line)
        if m:
          global_entity.dataclass_fields = global_entity.dataclass_fields or []
          global_entity.dataclass_fields.append(line.strip())

      m = CLASS_ASSIGNMENT_RE.match(line)
      if m:
        class_member = ClassMember(
            classname=global_entity.name, name=m.group(1), kind=ClassMemberType.ASSIGNMENT)

  return entities


def print_markdown(entities: List[CodeEntity]):
  # Dict from entity's name to its link text.
  anchors: Dict[str, str] = {}
  for entity in entities:
    if entity:
      name = entity.fullname()
      if name is not None:
        anchors[name] = name.replace(".", "").lower()

  for entity in entities:
    process_pydoc(entity, anchors)


if __name__ == "__main__":
  entities = parse_code_entities()
  print_markdown(entities)
