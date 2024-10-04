# SPDX-FileCopyrightText: © 2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""lib_java v2 distributed via minescript.net

Library for using Java reflection from Python, wrapping
the low-level Java API script functions (`java_*`).

Requires:
  minescript v4.0

Example:
```
from minescript import (echo, version_info)
from lib_java import (
  JavaClass, java_class_map, java_member_map)

# If using a version of Minecraft with obfuscated
# symbols, populate these dictionaries with the
# appropriate mappings, for example:
mc_class_name = version_info().minecraft_class_name
if mc_class_name == "net.minecraft.class_310":
  java_class_map.update({
    "net.minecraft.client.Minecraft": "net.minecraft.class_310",
  })
  java_member_map.update({
    "getInstance": "method_1551",
    "getFps": "method_47599",
  })

Minecraft = JavaClass("net.minecraft.client.Minecraft")
minecraft = Minecraft.getInstance()
echo("fps:", minecraft.getFps())
```
"""

from minescript import (
  JavaHandle,
  Task,
  java_access_field,
  java_array_index,
  java_array_length,
  java_assign,
  java_bool,
  java_call_method,
  java_call_script_function,
  java_class,
  java_ctor,
  java_double,
  java_float,
  java_int,
  java_member,
  java_new_instance,
  java_release,
  java_string,
  java_to_string,
  log,
  script_loop,
)
from minescript_runtime import debug_log
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Set, Tuple
import threading

# These script functions should be safe to call on any thread:
# TODO(maxuser): Include java_access_field?
for func in (
    java_array_index, java_array_length,
    java_class, java_double, java_float, java_int, java_bool,
    java_member, java_release, java_string, java_assign):
  func.set_required_executor(script_loop)

# Map from unobfuscated class name to the obfuscated name being used in Java.
java_class_map: Dict[str, str] = {}

# Map from unobfuscated method or field name to the obfuscated name being used in Java.
java_member_map: Dict[str, str] = {}

_inverse_java_member_map: Dict[str, str] = None

def get_unobfuscated_member_name(obfuscated_name: str) -> str:
  global _inverse_java_member_map
  if _inverse_java_member_map is None or len(_inverse_java_member_map) != len(java_member_map):
    _inverse_java_member_map = {v: k for k, v in java_member_map.items()}
  return _inverse_java_member_map.get(obfuscated_name)


class AutoReleasePool:
  def __init__(self):
    self.refs = []

  def __call__(self, ref: JavaHandle) -> JavaHandle:
    """Track `ref` for auto-release when this pool is deleted or goes out of scope.
    
    Returns:
      `ref` for convenient wrapping of functions returning a JavaHandle.
    """
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

class ClassInfo:
  def __init__(self, id: JavaHandle):
      self.id: JavaHandle = id
      self._class_name = None
      self._field_names: Set[str] = None

  def class_name(self) -> str:
    if self._class_name is None:
      with AutoReleasePool() as auto:
        jclass_name = auto(java_call_method(self.id, Class_getName_id))
        self._class_name = java_to_string(jclass_name)
    return self._class_name
    
  def field_names(self) -> Set[str]:
    if self._field_names is None:
      field_names = set()
      with script_loop:
        with AutoReleasePool() as auto:
          jfields_array = auto(java_call_method(self.id, Class_getFields_id))
          for i in range(java_array_length(jfields_array)):
            jfield = auto(java_array_index(jfields_array, i))
            jfield_name = auto(java_call_method(jfield, Field_getName_id))
            field_name = java_to_string(jfield_name)
            field_names.add(field_name)
            unobfuscated_name = get_unobfuscated_member_name(field_name)
            if unobfuscated_name is not None:
              field_names.add(unobfuscated_name)
      self._field_names = field_names
    return self._field_names


_class_info: Dict[int, ClassInfo] = {}

def _get_class_info(class_id: JavaHandle) -> ClassInfo:
  class_info = _class_info.get(class_id)
  if class_info is None:
    class_info = ClassInfo(class_id)
    _class_info[class_id] = class_info
  return class_info
  

def find_java_class(name: str):
  with script_loop:
    return java_class(java_class_map.get(name, name))

def find_java_member(clss, name: str):
  with script_loop:
    return java_member(clss, java_member_map.get(name, name))

_null_id = 0

_threaded_tasks = threading.local()  # Has attr "recorder" when recording.

class TaskRecorder:
  """Context for recording tasks when interacting with JavaObject fields and methods.
  
  Example:
    ```
    from minescript import script_loop, render_loop, run_tasks
    from lib_java import JavaClass, TaskRecorder

    task_recorder = TaskRecorder()

    # This assumes symbols aren't obfuscated. If they are,
    # update java_class_map and java_member_map from lib_java.
    # Recording on the script loop is generally safe and efficient.
    with script_loop:
      Minecraft = JavaClass("net.minecraft.client.Minecraft")
      
      # Record tasks for launching the player vertically.
      # Within the `with task_recorder:` code block, field and
      # method accesses on JavaObject instances are recorded
      # rather than executed.
      with task_recorder:
        minecraft = Minecraft.getInstance()
        player = minecraft.player
        player.setDeltaMovement(0., 2., 0.)

    # Run the recorded tasks on the render loop since that's where
    # interactions with game state generally need to be executed.
    with render_loop:
      run_tasks(task_recorder.recorded_tasks())
    ```
  """

  def __init__(self):
    self._tasks: List[Task] = []
    self._java_ref_tasks: List[Task] = []

  @staticmethod
  def active() -> "TaskRecorder":
    """Returns the active `TaskRecorder` for the current thread, or `None`."""
    if hasattr(_threaded_tasks, "recorder"):
      return _threaded_tasks.recorder
    else:
      return None
  
  def __bool__(self):
    """Always `True` as a convenience for checking `TaskRecorder.active` which may be `None`"""
    return True

  def __enter__(self):
    if hasattr(_threaded_tasks, "recorder"):
      raise RuntimeError(
            "Cannot record tasks while another TaskRecorder is already recording on this thread.")
    _threaded_tasks.recorder = self
    return self

  def __exit__(self, exc_type, exc_val, exc_tb):
    if not hasattr(_threaded_tasks, "recorder"):
      raise RuntimeError("No TaskRecorder currently recording on this thread.")
    if _threaded_tasks.recorder is not self:
      raise RuntimeError("A conflicting TaskRecorder was recording on this thread.")
    del _threaded_tasks.recorder

  def append_task(self, task: Task):
    self._tasks.append(task)

  def append_java_task(self, task: Task):
    self._tasks.append(task)
    self._java_ref_tasks.append(task)
  
  def recorded_tasks(self):
    return self._tasks + [java_release.as_task(*self._java_ref_tasks)]
  

with script_loop:
  Object_id = find_java_class("java.lang.Object")
  Object_getClass_id = find_java_member(Object_id, "getClass")

  Objects_id = find_java_class("java.util.Objects")
  Objects_isNull_id = find_java_member(Objects_id, "isNull")

  Class_id = find_java_class("java.lang.Class")
  Class_getName_id = find_java_member(Class_id, "getName")
  Class_getField_id = find_java_member(Class_id, "getField")
  Class_getFields_id = find_java_member(Class_id, "getFields")
  Class_getMethods_id = find_java_member(Class_id, "getMethods")
  Class_isEnum_id = find_java_member(Class_id, "isEnum")

  Field_id = find_java_class("java.lang.reflect.Field")
  Field_getType_id = find_java_member(Field_id, "getType")
  Field_getName_id = find_java_member(Field_id, "getName")

  Method_id = find_java_class("java.lang.reflect.Method")
  Method_getName_id = find_java_member(Method_id, "getName")
  Method_getReturnType_id = find_java_member(Method_id, "getReturnType")
  Method_getParameterCount_id = find_java_member(Method_id, "getParameterCount")

  Boolean_id = find_java_class("java.lang.Boolean")
  Integer_id = find_java_class("java.lang.Integer")
  Float_id = find_java_class("java.lang.Float")
  Double_id = find_java_class("java.lang.Double")

@dataclass
class Float:
  """Wrapper class for mirroring Java `float` in Python.
  
  Python `float` maps to Java `double`, and Python doesn't have a built-in single-precision float.
  """

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

def from_java_type(java_id: JavaHandle):
  # TODO(maxuser): Run most of these calls in script_loop, but not java_to_string when uncertain
  # that it's a primitive type.
  with AutoReleasePool() as auto:
    if java_id == _null_id or \
        java_to_string(auto(java_call_method(_null_id, Objects_isNull_id, java_id))) == "true":
      return None

    java_type = java_to_string(
      auto(java_call_method(auto(java_call_method(java_id, Object_getClass_id)), Class_getName_id)))
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

def _promote_primitive_types(type_id: JavaHandle) -> JavaHandle:
  type_name = _get_class_info(type_id).class_name()
  if type_name == "boolean":
    return Boolean_id
  if type_name == "int":
    return Integer_id
  if type_name == "float":
    return Float_id
  if type_name == "double":
    return Double_id
  return type_id

class JavaRef:
  def __init__(self, id: JavaHandle):
    self.id = id
    self.count = 1

  def increment(self):
    self.count += 1

  def decrement(self):
    self.count -= 1
    if self.count <= 0:
      debug_log(f"del JavaRef {self.id}")
      java_release(self.id)

class JavaObject:
  """Python representation of a Java object."""

  def __init__(self, target_id: JavaHandle, ref: JavaRef = None):
    """Constructs a Python handle to a Java object given a `JavaHandle`. """
    self.id = target_id
    if ref is None:
      self.ref = JavaRef(target_id)
    else:
      ref.increment()
      self.ref = ref
    self._class_id = None
    self.is_array = None

  def __repr__(self):
    class_name = _get_class_info(self.get_class_id()).class_name()
    return f'JavaObject("{class_name}")'

  def toString(self) -> str:
    """Returns a `str` representation of `this.toString()` from Java."""
    return java_to_string(self.id)

  def get_class_id(self):
    if self._class_id is None:
      self._class_id = java_call_method(self.id, Object_getClass_id)
    return self._class_id

  def set_value(self, value: Any):
    """Sets this JavaObject to reference `value` instead.
    
    `value` can be any of the following types:
    - bool: converted to Java Boolean
    - int: converted to Java Integer
    - Float: converted to Java Float
    - float: converted to Java Double
    - str: converted to Java String
    - JavaObject: this JavaObject will reference the same Java object as `value`
    """
    if value is None:
      java_assign(self.id, 0)
      return

    def set_java_primitive(java_primitive_ctor: Callable[[Any], int], value: Any):
      jvalue = java_primitive_ctor(value)
      java_assign(self.id, jvalue)
      java_release(jvalue)

    t = type(value)
    if t is bool:
      set_java_primitive(java_bool, value)
    elif t is int:
      set_java_primitive(java_int, value)
    elif t is Float:
      set_java_primitive(java_float, value.value)
    elif t is float:
      set_java_primitive(java_double, value)
    elif t is str:
      set_java_primitive(java_string, value)
    elif isinstance(value, JavaObject):
      java_assign(self.value.id, value.id)
    else:
      raise ValueError(f"Python type {type(value)} not convertible to Java: {value}")

  def __str__(self):
    return java_to_string(self.id)

  def __del__(self):
    if self.ref is not None:
      self.ref.decrement()

  def __getattr__(self, name: str):
    """Accesses the field or method named `name`.
    
    Args:
      name: name of a field or method on this JavaObject's class
    
    Returns:
      If `name` matches a field on this JavaObject's class, then return the
      value of that field as a Python primitive or new JavaObject. Otherwise
      return a `JavaBoundMember` equivalent to the Java expression
      `this::methodName`.
    """
    binding = JavaBoundMember(self.get_class_id(), self.id, name, ref=self.ref)

    task_recorder = TaskRecorder.active()
    if task_recorder:
      log(f"Recording JavaObject get `{name}`")
      log(f"Field names for `{_get_class_info(self.get_class_id()).class_name()}`: {_get_class_info(self.get_class_id()).field_names()}")
      if name not in _get_class_info(self.get_class_id()).field_names():
        return binding
      try:
        field_id = java_call_method(self.get_class_id(), Class_getField_id, java_string(name))
        field_type_id = _promote_primitive_types(java_call_method(field_id, Field_getType_id))
        task = RecordedTask(
            field_type_id,
            java_access_field.as_task(self.id, binding.member_id),
            f"field `{name}`")
        task_recorder.append_java_task(task.task)
        return task
      except Exception as e:
        debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
        return binding

    if name not in _get_class_info(self.get_class_id()).field_names():
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

  def __len__(self) -> int:
    """If this JavaObject represents a Java array, returns the length of the array.
    
    Raises `TypeError` if this isn't an array.
    """
    if self._is_array():
      return java_array_length(self.id)
    else:
      raise TypeError(f"object {self.id} has no len()")

  def __getitem__(self, i: int):
    """If this JavaObject represents a Java array, returns `array[i]`.
    
    Args:
      i: index into array from which to get an element
    
    Returns:
      `array[i]` as a Python primitive value or JavaObject.
    
    Raises:
      `TypeError` if this isn't an array.
    """
    if self._is_array():
      return from_java_type(java_array_index(self.id, i))
    else:
      raise TypeError(f"object {self.id} is not subscriptable")


class JavaBoundMember:
  """Representation of a Java method reference in Python."""
  def __init__(self, target_class_id: JavaHandle, target, name: str, ref: JavaRef = None):
    """Member that's bound to a target object, representing a field or method.

    Args:
      target_class_id: Java object ID of enclosing class for this member
      target: either Java object ID of the target through which this member is accessed, or
          Task for scheduled execution
      name: name of this member
    """
    self.ref = ref
    if ref is not None:
      ref.increment()

    self.target_class_id = target_class_id
    self.target = target
    self.member_name = java_member_map.get(name, name)
    self.member_id = find_java_member(target_class_id, name)
  
  def __del__(self):
    if self.ref is not None:
      debug_log(f"Deleting bound member {self.member_name}")
      self.ref.decrement()

  def __repr__(self):
    return f"(target_class_id={self.target_class_id}, target={self.target}, member_name={self.member_name}, member_id={self.member_id})"

  def __call__(self, *args):
    """Calls the bound method with the given `args`.
    
    Returns:
      A Python primitive (bool, int, float, str) if applicable, otherwise a JavaObject.
    """
    task_recorder = TaskRecorder.active()
    if task_recorder:
      log(f"Recording method call `{self.member_name}` on class {self.target_class_id}")
      # Need to iterate all methods to find a method with matching name and arg count to determine
      # the return type to use for dependent expressions or tasks.
      methods = java_call_method(self.target_class_id, Class_getMethods_id)
      target_class_name = _get_class_info(self.target_class_id).class_name()
      num_methods = java_array_length(methods)
      log(f"Checking {num_methods} methods from `{target_class_name}` to match `{self.member_name}`")
      with AutoReleasePool() as auto:
        for i in range(num_methods):
          method = java_array_index(methods, i)
          method_name = java_to_string(auto(java_call_method(method, Method_getName_id)))
          num_args = int(java_to_string(auto(java_call_method(method, Method_getParameterCount_id))))
          if method_name == self.member_name and num_args == len(args):
            return_type_id = _promote_primitive_types(
                java_call_method(method, Method_getReturnType_id))
            task = RecordedTask(
                return_type_id,
                java_call_method.as_task(self.target, self.member_id,
                    *[to_java_type(a) for a in args]),
                f"method `{self.member_name}`")
            task_recorder.append_java_task(task.task)
            return task
      raise ValueError(f"No method found named `{self.member_name}` with {len(args)} arg(s).")

    if type(self.target) is Task:
      raise ValueError(f"Unexpected Task outside of recording mode: {self.target}")

    result = java_call_method(self.target, self.member_id,
      *[to_java_type(a) for a in args])
    return from_java_type(result)


class JavaInt(JavaObject):
  """JavaObject subclass for Java Integer."""
  def __init__(self, value: int):
      super().__init__(java_int(value))


class JavaFloat(JavaObject):
  """JavaObject subclass for Java Float."""
  def __init__(self, value: float):
      super().__init__(java_float(value))


class JavaString(JavaObject):
  """JavaObject subclass for Java String."""
  def __init__(self, value: str):
      super().__init__(java_string(value))

  
class JavaClass(JavaObject):
  """JavaObject subclass for Java class objects."""
  def __init__(self, name):
    super().__init__(find_java_class(name))
    self.class_name = name
    self.ctor: JavaHandle = None
    self._is_enum: bool = None
    debug_log(f"Creating class {name} with id {self.id}")

  def __repr__(self):
    return f'JavaClass("{self.class_name}")'

  def is_enum(self):
    """Returns `True` if this class represents a Java enum type."""
    if self._is_enum is None:
      with AutoReleasePool() as auto:
        self._is_enum = from_java_type(auto(java_call_method(self.id, Class_isEnum_id)))
    return self._is_enum

  def __getattr__(self, name):
    """Accesses the static field or static method named `name` on this Java class.
    
    Args:
      name: name of a static field or static method on this Java class.
    
    Returns:
      If `name` matches a static field on this Java class, then return the value of that field as
      a new JavaObject. Otherwise return a `JavaBoundMember` equivalent to the Java expression
      `ThisClass::staticMethodName`.
    """
    if name == "class_":
      return JavaObject(self.id, ref=self.ref)

    if self.is_enum():
      valueOf = find_java_member(self.id, "valueOf")
      with AutoReleasePool() as auto:
        result = JavaObject(java_call_method(_null_id, valueOf, auto(java_string(name))))
        return result

    binding = JavaBoundMember(self.id, _null_id, name)

    task_recorder = TaskRecorder.active()
    if task_recorder:
      log(f"Recording JavaClass get `{name}`")
      # TODO(maxuser): Provide disambiguation when field and method have same name, e.g. "foo.m_name()"
      log(f"Field names for `{self.class_name}`: {_get_class_info(self.id).field_names()}")
      if name not in _get_class_info(self.id).field_names():
        return binding
      try:
        field_id = java_call_method(self.id, Class_getField_id, java_string(name))
        field_type_id = _promote_primitive_types(java_call_method(field_id, Field_getType_id))
        task = RecordedTask(
            field_type_id,
            java_access_field.as_task(self.id, binding.member_id),
            f"field `{name}`")
        task_recorder.append_java_task(task.task)
        return task
      except Exception as e:
        debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
        log(f"Returning binding for JavaClass `{name}`")
        return binding

    # TODO(maxuser): Provide disambiguation when field and method have same name, e.g. "foo.m_name()"
    if name not in _get_class_info(self.id).field_names():
      return binding
    try:
      field = java_access_field(self.id, binding.member_id)
      return from_java_type(field)
    except Exception as e:
      debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
      return binding

  def __call__(self, *args):
    """Calls the constructor for this Java class that takes the given `args`, if applicable.
    
    Returns:
      JavaObject representing the newly constructed Java object.
    """
    if self.ctor is None:
      self.ctor = JavaObject(java_ctor(self.id))

    task_recorder = TaskRecorder.active()
    if task_recorder:
      task = RecordedTask(
          self.id,
          java_new_instance.as_task(self.ctor.id, *[to_java_type(a) for a in args]),
          f"ctor `{self.class_name}`")
      task_recorder.append_java_task(task.task)
      return task

    return JavaObject(java_new_instance(self.ctor.id, *[to_java_type(a) for a in args]))


class RecordedTask:
  def __init__(self, type_id: JavaHandle, task: Task, desc: str):
    self.type_id = type_id
    self.task = task
    self.desc = desc
    log("recorded task:", desc)

  def __getattr__(self, name):
    binding = JavaBoundMember(self.type_id, self.task, name)

    task_recorder = TaskRecorder.active()
    if not task_recorder:
      raise RuntimeError(
          f"Cannot call getattr '{name}' on RecordedTask with recording mode disabled: {self.task}")

    if name not in _get_class_info(self.type_id).field_names():
      return binding
    try:
      with AutoReleasePool() as auto:
        field_id = auto(java_call_method(self.type_id, Class_getField_id, java_string(name)))
        field_type_id = _promote_primitive_types(java_call_method(field_id, Field_getType_id))
        task = RecordedTask(
            field_type_id,
            java_access_field.as_task(self.task, binding.member_id),
            f"field `{name}`")
        task_recorder.append_java_task(task.task)
        return task
    except Exception as e:
      debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
      return binding

null = JavaObject(0)

def callScriptFunction(func_name: str, *args) -> JavaObject:
  """Calls the given Minescript script function.
  
  Args:
    func_name: name of a Minescript script function
    args: args to pass to the given script function
  
  Returns:
    The return value of the given script function as a Python primitive type or JavaObject.
  """
  return from_java_type(java_call_script_function(func_name, *[to_java_type(a) for a in args]))

class JavaFuture:
  """Java value that will become available in the future when an async function completes."""

  def __init__(self, future):
    self.future = future

  def wait(self, timeout=None):
    """Waits for the async function to complete.
    
    Args:
      timeout: if not `None`, timeout in seconds to wait on the async function to complete
    
    Returns:
      Python primitive value or JavaObject returned from the async function upon completion.
    """
    return from_java_type(self.future.wait(timeout=timeout))

def callAsyncScriptFunction(func_name: str, *args) -> JavaFuture:
  """Calls the given Minescript script function asynchronously.
  
  Args:
    func_name: name of a Minescript script function
    args: args to pass to the given script function
  
  Returns:
    `JavaFuture` that will hold the return value of the async funcion when complete.
  """
  return JavaFuture(java_call_script_function.as_async(func_name, *[to_java_type(a) for a in args]))
