# SPDX-FileCopyrightText: © 2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""lib_java v1 distributed via minescript.net

Library for using Java reflection from Python, wrapping
the low-level Java API provided via java_* script functions.

Requires:
  minescript v4.0

Example:
  from minescript import echo
  from lib_java import JavaClass

  # This example requires a version of Minecraft with
  # unobfuscated symbols, like dev-mode launchers or
  # NeoForge.
  Minecraft = JavaClass("net.minecraft.client.Minecraft")
  minecraft = Minecraft.getInstance()
  echo("fps:", minecraft.getFps())
"""

from minescript import (
    Task, script_loop, render_loop, echo, log,
    java_access_field, java_array_index, java_array_length, java_bool, java_call_method,
    java_class, java_ctor, java_double, java_float, java_int, java_member, java_new_instance,
    java_release, java_string, java_to_string
)
from minescript_runtime import debug_log
from dataclasses import dataclass
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

for func in (
    java_access_field, java_array_index, java_array_length, java_bool, java_call_method,
    java_class, java_ctor, java_double, java_float, java_int, java_member, java_new_instance,
    java_release, java_string, java_to_string):
  func.set_default_executor(script_loop)

_null_id = 0

@dataclass
class RecordedTaskList:
  tasks: List[Task]
  refs: List[int] # Java references that can be released if/when the scheduled tasks are canceled.

  def append(self, task: Task):
    self.tasks.append(task)

_is_recording = False
_recorded_tasks = None

def start_recording_tasks():
  global _is_recording, _recorded_tasks
  _is_recording = True
  _recorded_tasks = RecordedTaskList([], [])

def stop_recording_tasks() -> Tuple[List[Task], List[int]]:
  global _is_recording, _recorded_tasks
  _is_recording = False

  tasks = _recorded_tasks
  _recorded_tasks = None

  return tasks


Object_id = java_class("java.lang.Object")
Object_getClass_id = java_member(Object_id, "getClass")

Objects_id = java_class("java.util.Objects")
Objects_isNull_id = java_member(Objects_id, "isNull")

Class_id = java_class("java.lang.Class")
Class_getName_id = java_member(Class_id, "getName")
Class_getField_id = java_member(Class_id, "getField")
Class_getMethods_id = java_member(Class_id, "getMethods")
Class_isEnum_id = java_member(Class_id, "isEnum")

Field_id = java_class("java.lang.reflect.Field")
Field_getType_id = java_member(Field_id, "getType")

Method_id = java_class("java.lang.reflect.Method")
Method_getName_id = java_member(Method_id, "getName")
Method_getReturnType_id = java_member(Method_id, "getReturnType")
Method_getParameterCount_id = java_member(Method_id, "getParameterCount")

Boolean_id = java_class("java.lang.Boolean")
Integer_id = java_class("java.lang.Integer")
Float_id = java_class("java.lang.Float")
Double_id = java_class("java.lang.Double")

class JavaReleasePool:
  def __init__(self):
    self.refs = []

  def __call__(self, ref):
    """Track `ref` for auto-release when this pool is deleted or goes out of scope."""
    self.refs.append(ref)
    return ref

  def release_all(self):
    if self.refs:
      java_release(*self.refs)
      self.refs.clear()

  def __enter__(self):
    return self

  def __exit__(self, exc_type, exc_val, exc_tb):
    self.release_all()

  def __del__(self):
    self.release_all()

@dataclass
class Float:
  value: float

  def __str__(self):
    return f"{self.value}f"

def to_java_type(value):
  if value is None:
    return _null_id

  t = type(value)
  if t is bool:
    return java_bool(value)
  elif t is int:
    return java_int(value)
  elif t is Float:
    return java_float(value.value)
  elif t is float:
    return java_double(value)
  elif t is str:
    return java_string(value)
  elif isinstance(value, RecordedTask):
    return value.task
  elif isinstance(value, JavaObject):
    return value.id
  else:
    raise ValueError(f"Python type {type(value)} not convertible to Java: {value}")

def from_java_type(java_id):
  if java_id == _null_id or \
      java_to_string(java_call_method(_null_id, Objects_isNull_id, java_id)) == "true":
    return None

  java_type = java_to_string(
    java_call_method(java_call_method(java_id, Object_getClass_id), Class_getName_id))
  if java_type == "java.lang.Boolean":
    return java_to_string(java_id) == "true"
  elif java_type == "java.lang.Integer":
    return int(java_to_string(java_id))
  elif java_type == "java.lang.Float":
    return Float(float(java_to_string(java_id)))
  elif java_type == "java.lang.Double":
    return float(java_to_string(java_id))
  elif java_type == "java.lang.String":
    return java_to_string(java_id)
  else:
    return JavaObject(java_id)

def _promote_primitive_types(type_id: int):
  type_name = java_to_string(java_call_method(type_id, Class_getName_id))
  if type_name == "boolean":
    return Boolean_id
  if type_name == "int":
    return Integer_id
  if type_name == "float":
    return Float_id
  if type_name == "double":
    return Double_id
  return type_id

class JavaObject:
  def __init__(self, target_id: int, own=True):
    self.id = target_id
    self.class_id = None
    self.own = own
    self.is_array = None

  def __repr__(self):
    class_name = java_to_string(java_call_method(self.get_class_id(), Class_getName_id))
    return f'JavaObject("{class_name}")'

  def toString(self):
    return java_to_string(self.id)

  def get_class_id(self):
    if self.class_id is None:
      self.class_id = java_call_method(self.id, Object_getClass_id)
    return self.class_id

  def __str__(self):
    return java_to_string(self.id)

  def __del__(self):
    if self.own:
      java_release(self.id)

  def __getattr__(self, name):
    binding = JavaBoundMember(self.get_class_id(), self.id, name)

    if _is_recording:
      log(f"Recording JavaObject get `{name}`")
      try:
        field_id = java_call_method(self.get_class_id(), Class_getField_id, java_string(name))
        field_type_id = _promote_primitive_types(java_call_method(field_id, Field_getType_id))
        task = RecordedTask(
            field_type_id,
            java_access_field.as_task(self.id, binding.member_id),
            f"field `{name}`")
        _recorded_tasks.append(task.task)
        return task
      except Exception as e:
        debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
        return binding

    try:
      field = java_access_field(self.id, binding.member_id)
      return from_java_type(field)
    except Exception as e:
      debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
      return binding

  def _is_array(self):
    if self.is_array is None:
      self_class = java_call_method(self.id, Object_getClass_id)
      classname = java_to_string(java_call_method(self_class, Class_getName_id))
      self.is_array = classname.startswith("[")
    return self.is_array

  def __len__(self):
    if self._is_array():
      return java_array_length(self.id)
    else:
      raise TypeError(f"object {self.id} has no len()")

  def __getitem__(self, i):
    if self._is_array():
      return JavaObject(java_array_index(self.id, i))
    else:
      raise TypeError(f"object {self.id} is not subscriptable")

class JavaBoundMember:
  def __init__(self, target_class_id: int, target, name: str):
    """Member that's bound to a target object, representing a field or method.

    Args:
      target_class_id: Java object ID of enclosing class for this member
      target: either Java object ID of the target through which this member is accessed, or
          Task for scheduled exexecution
      name: name of this member
    """
    self.target_class_id = target_class_id
    self.target = target
    self.member_name = name
    self.member_id = java_member(target_class_id, name)

  def __repr__(self):
    return f"(target_class_id={self.target_class_id}, target={self.target}, member_name={self.member_name}, member_id={self.member_id})"

  def __call__(self, *args):
    if _is_recording:
      log(f"Recording method call `{self.member_name}` on class {self.target_class_id}")
      # Need to iterate all methods to find a method with matching name and arg count to determine
      # the return type to use for dependent expressions or tasks.
      methods = java_call_method(self.target_class_id, Class_getMethods_id)
      log(f"Getting array length of getMethods(`{self.member_name}`)")
      num_methods = java_array_length(methods)
      log("Num methods:", num_methods)
      for i in range(num_methods):
        method = java_array_index(methods, i)
        method_name = java_to_string(java_call_method(method, Method_getName_id))
        #log(f"method[{i}]: {method_name}")
        num_args = int(java_to_string(java_call_method(method, Method_getParameterCount_id)))
        if method_name == self.member_name and num_args == len(args):
          return_type_id = _promote_primitive_types(
              java_call_method(method, Method_getReturnType_id))
          task = RecordedTask(
              return_type_id,
              java_call_method.as_task(self.target, self.member_id,
                  *[to_java_type(a) for a in args]),
              f"method `{self.member_name}`")
          _recorded_tasks.append(task.task)
          return task
      raise ValueError(f"No method found named `{self.member_name}` with {len(args)} arg(s).")

    if type(self.target) is Task:
      raise ValueError(f"Unexpected Task outside of recording mode: {self.target}")

    result = java_call_method(self.target, self.member_id,
      *[to_java_type(a) for a in args])
    return from_java_type(result)

class JavaClass(JavaObject):
  def __init__(self, name):
    super().__init__(java_class(name))
    self.class_name = name
    self.ctor = None

  def __repr__(self):
    return f'JavaClass("{self.class_name}")'

  def __getattr__(self, name):
    if name == "class_":
      return JavaObject(self.id, own=False)

    if from_java_type(java_call_method(self.id, Class_isEnum_id)):
      valueOf = java_member(self.id, "valueOf")
      return JavaObject(java_call_method(_null_id, valueOf, java_string(name)))

    binding = JavaBoundMember(self.id, _null_id, name)

    if _is_recording:
      log(f"Recording JavaClass get `{name}`")
      try:
        field_id = java_call_method(self.id, Class_getField_id, java_string(name))
        field_type_id = _promote_primitive_types(java_call_method(field_id, Field_getType_id))
        task = RecordedTask(
            field_type_id,
            java_access_field.as_task(self.id, binding.member_id),
            f"field `{name}`")
        _recorded_tasks.append(task.task)
        return task
      except Exception as e:
        debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
        log(f"Returning binding for JavaClass `{name}`")
        return binding

    try:
      field = java_access_field(self.id, binding.member_id)
      return from_java_type(field)
    except Exception as e:
      debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
      return binding

  def __call__(self, *args):
    if self.ctor is None:
      self.ctor = java_ctor(self.id)

    if _is_recording:
      task = RecordedTask(
          self.id,
          java_new_instance.as_task(self.ctor, *[to_java_type(a) for a in args]),
          f"ctor `{self.class_name}`")
      _recorded_tasks.append(task.task)
      return task

    return JavaObject(java_new_instance(self.ctor, *[to_java_type(a) for a in args]))


class RecordedTask:
  def __init__(self, type_id: int, task: Task, desc: str):
    self.type_id = type_id
    self.task = task
    self.desc = desc
    log("recorded task:", desc)

  def __getattr__(self, name):
    binding = JavaBoundMember(self.type_id, self.task, name)

    if not _is_recording:
      raise RuntimeError(
          f"Cannot call getattr on RecordedTask with recording mode disabled: {self.task}")

    try:
      field_id = java_call_method(self.type_id, Class_getField_id, java_string(name))
      field_type_id = _promote_primitive_types(java_call_method(field_id, Field_getType_id))
      task = RecordedTask(
          field_type_id,
          java_access_field.as_task(self.task, binding.member_id),
          f"field `{name}`")
      _recorded_tasks.append(task.task)
      return task
    except Exception as e:
      debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
      return binding

null = JavaObject(0, own=False)

