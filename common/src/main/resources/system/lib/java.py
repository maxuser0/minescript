# SPDX-FileCopyrightText: © 2024-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""java v5.0 distributed via Minescript jar file

Library for using Java reflection from Python, wrapping
the low-level Java API script functions (`java_*`).

Example:
```
from minescript import echo
from java import JavaClass

Minecraft = JavaClass("net.minecraft.client.Minecraft")
minecraft = Minecraft.getInstance()
echo("fps:", minecraft.getFps())
```
"""

from minescript import (
  JavaHandle,
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
  java_field_names,
  java_float,
  java_int,
  java_member,
  java_new_array,
  java_new_instance,
  java_release,
  java_string,
  java_to_string,
  log,
  script_loop,
  ScriptFunction,
)
from minescript_runtime import debug_log
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Set, Tuple

# These script functions should be safe to call on any thread:
for func in (
    java_array_index, java_array_length, java_assign, java_bool, java_class, java_double,
    java_field_names, java_float, java_int, java_member, java_release, java_string):
  func.set_required_executor(script_loop)


class AutoReleasePool:
  """Context manager for managing Java references in Python using `with` blocks.
  
  Example:
    ```
    import minescript
    from java import *
    with AutoReleasePool() as auto:
      Object_class = auto(java_class("java.lang.Object"))
      Object_hashCode = auto(java_member(Object_class, "hashCode"))
      hello_string = auto(java_string("hello"))
      hash_value  = auto(java_call_method(hello_string, Object_hashCode))
      minescript.echo(java_to_string(hash_value))
    ```

    All the Java references captured with `auto()` in the example are released when
    the `with` block above exits, automatically calling [`java_release()`](#java_release).
  """
  
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


def _find_java_class(name: str):
  with script_loop:
    return java_class(name)

def _find_java_member(clss, name: str):
  with script_loop:
    return java_member(clss, name)

_null_id = 0

with script_loop:
  Object_id = _find_java_class("java.lang.Object")
  Object_getClass_id = _find_java_member(Object_id, "getClass")

  Objects_id = _find_java_class("java.util.Objects")
  Objects_isNull_id = _find_java_member(Objects_id, "isNull")

  Class_id = _find_java_class("java.lang.Class")
  Class_getName_id = _find_java_member(Class_id, "getName")
  Class_getField_id = _find_java_member(Class_id, "getField")
  Class_getFields_id = _find_java_member(Class_id, "getFields")
  Class_getMethods_id = _find_java_member(Class_id, "getMethods")
  Class_isEnum_id = _find_java_member(Class_id, "isEnum")
  Class_isAssignableFrom_id =_find_java_member(Class_id, "isAssignableFrom")

  Field_id = _find_java_class("java.lang.reflect.Field")
  Field_getType_id = _find_java_member(Field_id, "getType")
  Field_getName_id = _find_java_member(Field_id, "getName")

  Method_id = _find_java_class("java.lang.reflect.Method")
  Method_getName_id = _find_java_member(Method_id, "getName")
  Method_getReturnType_id = _find_java_member(Method_id, "getReturnType")
  Method_getParameterCount_id = _find_java_member(Method_id, "getParameterCount")

  Boolean_id = _find_java_class("java.lang.Boolean")
  Integer_id = _find_java_class("java.lang.Integer")
  Float_id = _find_java_class("java.lang.Float")
  Double_id = _find_java_class("java.lang.Double")

  Array_id = _find_java_class("java.lang.reflect.Array")
  Array_newInstance_id = _find_java_member(Array_id, "newInstance")
  Array_set_id = _find_java_member(Array_id, "set")
  Array_get_id = _find_java_member(Array_id, "get")
  Array_getLength_id = _find_java_member(Array_id, "getLength")

  Iterable_id = _find_java_class("java.lang.Iterable")

  Collection_id = _find_java_class("java.util.Collection")
  Collection_size_id = _find_java_member(Collection_id, "size")
  Collection_contains_id = _find_java_member(Collection_id, "contains")

  # Support for Pyjinn built-in types:
  PyObject_id = _find_java_class("org.pyjinn.interpreter.Script$PyObject")
  PyObject_callMethod_id = _find_java_member(PyObject_id, "callMethod")
  PyObject_type_id = _find_java_member(PyObject_id, "type")
  PyObject_dict_id = _find_java_member(PyObject_id, "__dict__")
  PyClass_id = _find_java_class("org.pyjinn.interpreter.Script$PyClass")
  PyClass_name_id = _find_java_member(PyClass_id, "name")
  PyDict_id = _find_java_class("org.pyjinn.interpreter.Script$PyDict")
  PyDict_contains_id = _find_java_member(PyDict_id, "__contains__")
  PyDict_get_id = _find_java_member(PyDict_id, "get")
  Lengthable_id = _find_java_class("org.pyjinn.interpreter.Script$Lengthable")
  Lengthable_len_id = _find_java_member(Lengthable_id, "__len__")
  ItemGetter_id = _find_java_class("org.pyjinn.interpreter.Script$ItemGetter")
  ItemGetter_getitem_id = _find_java_member(ItemGetter_id, "__getitem__")
  ItemContainer_id = _find_java_class("org.pyjinn.interpreter.Script$ItemContainer")
  ItemContainer_contains_id = _find_java_member(ItemContainer_id, "__contains__")

@dataclass
class Float:
  """Wrapper class for mirroring Java `float` in Python.
  
  Python `float` maps to Java `double`, and Python doesn't have a built-in single-precision float.
  """

  value: float

  def __bool__(self):
    return bool(self.value)

  def __str__(self):
    return f"{self.value}f"

def to_java_handle(value):
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
  elif isinstance(value, JavaObject):
    value.ref.increment()
    return value.id
  else:
    raise ValueError(f"Python type {type(value)} not convertible to Java: {value}")


def from_java_handle(java_id: JavaHandle, java_object=None, script_env: "JavaObject" = None):
  """Converts Java object to a Python object.

  Takes ownership of the `java_id` reference.
  
  Args:
    java_id: handle to a Java object
    java_object: returned if given and value isn't convertible to a Python primitive
    script_env: JavaObject referencing org.pyjinn.interpreter.Script$Environment, or None
  """
  
  # TODO(maxuser): Run most of these calls in script_loop, but not java_to_string when uncertain
  # that it's a primitive type.
  with AutoReleasePool() as auto:
    if java_id == _null_id or \
        java_to_string(auto(java_call_method(_null_id, Objects_isNull_id, java_id))) == "true":
      return None

    java_type = java_to_string(
      auto(java_call_method(auto(java_call_method(java_id, Object_getClass_id)), Class_getName_id)))
    if java_type == "java.lang.Boolean":
      return java_to_string(auto(java_id)) == "true"
    elif java_type == "java.lang.Integer":
      return int(java_to_string(auto(java_id)))
    elif java_type == "java.lang.Float":
      return Float(float(java_to_string(auto(java_id))))
    elif java_type == "java.lang.Double":
      return float(java_to_string(auto(java_id)))
    elif java_type == "java.lang.String":
      return java_to_string(auto(java_id))
    elif java_object is not None:
      return java_object
    else:
      return JavaObject(java_id, script_env=script_env)

class JavaRef:
  """Reference counter for Java objects referenced from Python."""
  
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


class _JavaArrayIter:
  def __init__(self, java_array: "JavaObject"):
    self.java_array = java_array
    self.index = 0
    self.length = len(java_array)

  def __next__(self):
    if self.index < self.length:
      item = self.java_array[self.index]
      self.index += 1
      return item
    else:
      raise StopIteration


class _JavaIterator:
  def __init__(self, java_object: "JavaObject"):
    self.iter = java_object.iterator()

  def __next__(self) -> "JavaObject":
    if not self.iter.hasNext():
      raise StopIteration
    return self.iter.next()


class JavaObject:
  """Python representation of a Java object."""

  def __init__(self, target_id: JavaHandle, ref: JavaRef = None, is_script: bool = False, script_env: "JavaObject" = None):
    """Constructs a Python handle to a Java object given a `JavaHandle`. """
    self.id = target_id
    if ref is None:
      self.ref = JavaRef(target_id)
    else:
      ref.increment()
      self.ref = ref
    self._class_id = None
    self._class_name = None
    self.is_array = None
    self._is_script = is_script
    self.script_env = script_env

  def __repr__(self):
    return f'JavaObject("{self.get_class_name()}")'

  def toString(self) -> str:
    """Returns a `str` representation of `this.toString()` from Java."""
    return java_to_string(self.id)

  def get_class_id(self):
    if self._class_id is None:
      self._class_id = java_call_method(self.id, Object_getClass_id)
    return self._class_id
  
  def get_class_name(self):
    if self._class_name is None:
      self._class_name = java_to_string(java_call_method(self.get_class_id(), Class_getName_id))
    return self._class_name

  def __str__(self):
    return java_to_string(self.id)

  def __del__(self):
    if self.ref is not None:
      self.ref.decrement()
    if self._class_id is not None:
      java_release(self._class_id)

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
    is_pyjinn_object = from_java_handle(
        java_call_method(PyObject_id, Class_isAssignableFrom_id, self.get_class_id()))
    if is_pyjinn_object:
      def call_pyjinn_method(*args):
        with AutoReleasePool() as auto:
          params = auto(java_call_method(_null_id, Array_newInstance_id, Object_id, auto(java_int(len(args)))))
          for i, arg in enumerate(args):
            auto(java_call_method(_null_id, Array_set_id, params, auto(java_int(i)), auto(to_java_handle(arg))))
          result = auto(java_call_method(
                  self.id,
                  PyObject_callMethod_id,
                  auto(to_java_handle(self.script_env)),
                  auto(java_string(name)),
                  params))
          if from_java_handle(java_call_method(_null_id, Array_getLength_id, result)) > 0:
            return from_java_handle(java_call_method(_null_id, Array_get_id, result, auto(java_int(0))))
          else:
            pyj_type = auto(java_access_field(self.id, PyObject_type_id))
            type_name = from_java_handle(java_access_field(pyj_type, PyClass_name_id))
            raise TypeError(f"Pyjinn object of type '{type_name}' has no member named '{name}'")

      # First check if Pyjinn object has a field with the given name.
      with AutoReleasePool() as auto:
        pyj_dict = auto(java_access_field(self.id, PyObject_dict_id))
        jname = auto(java_string(name))
        if from_java_handle(java_call_method(pyj_dict, PyDict_contains_id, jname)):
          return from_java_handle(java_call_method(pyj_dict, PyDict_get_id, jname))

      # Fall back to a Pyjinn method.
      # TODO(maxuser): Check that there's a method with the given name, and throw an exception if
      # there isn't.
      return call_pyjinn_method

    binding = JavaBoundMember(
        self.get_class_id(), self.id, name, ref=self.ref, script=self if self._is_script else None)

    # TODO(maxuser): Cache field names per class name at script level (not per class ID which is not 1:1 with Java class).
    if name not in java_field_names(self.get_class_id()):
      return binding

    try:
      field = java_access_field(self.id, binding.member_id)
      return from_java_handle(field)
    except Exception as e:
      debug_log(f"lib_java.py: caught exception accessing field `{name}`: {e}")
      return binding

  def _is_array(self):
    if self.is_array is None:
      self.is_array = self.get_class_name().startswith("[")
    return self.is_array

  def __len__(self) -> int:
    """If this JavaObject represents a Java array or container, returns its length.
    
    Raises `TypeError` if this isn't a Java array or container.
    """
    if self._is_array():
      return java_array_length(self.id)

    with AutoReleasePool() as auto:
      is_lengthable = from_java_handle(
          java_call_method(Lengthable_id, Class_isAssignableFrom_id, self.get_class_id()))
      if is_lengthable:
        return from_java_handle(java_call_method(self.id, Lengthable_len_id))

    with AutoReleasePool() as auto:
      is_collection = from_java_handle(
          java_call_method(Collection_id, Class_isAssignableFrom_id, self.get_class_id()))
      if is_collection:
        return from_java_handle(java_call_method(self.id, Collection_size_id))

    raise TypeError(f"object {repr(self)} has no len()")

  def __iter__(self):
    if self._is_array():
      return _JavaArrayIter(self)

    with AutoReleasePool() as auto:
      is_iterable = from_java_handle(
          java_call_method(Iterable_id, Class_isAssignableFrom_id, self.get_class_id()))
    if is_iterable:
      return _JavaIterator(self)

    raise TypeError(f"Java object {repr(self)} it not iterable")

  def __contains__(self, element) -> int:
    """If this JavaObject represents a Java array or container, returns `True` if it contains `element`.
    
    Raises `TypeError` if this isn't a Java array or container.
    """
    if self._is_array():
      length = java_array_length(self.id)
      for i in range(length):
        if from_java_handle(java_array_index(self.id, i)) == element:
          return True
      return False

    with AutoReleasePool() as auto:
      is_container = from_java_handle(
          java_call_method(ItemContainer_id, Class_isAssignableFrom_id, self.get_class_id()))
      if is_container:
        return from_java_handle(java_call_method(self.id, ItemContainer_contains_id, auto(to_java_handle(element))))
    
    with AutoReleasePool() as auto:
      is_collection = from_java_handle(
          java_call_method(Collection_id, Class_isAssignableFrom_id, self.get_class_id()))
      if is_collection:
        return from_java_handle(java_call_method(self.id, Collection_contains_id, auto(to_java_handle(element))))

    raise TypeError(f"object {repr(self)} has no len()")

  def __bool__(self) -> int:
    """Returns False if the Java reference is null."""
    value = from_java_handle(self.id, java_object=self)
    if value in (None, False, 0, 0., ""):
      return False
    if type(value) is Float:
      return bool(value)
    if self._is_array():
      return java_array_length(self.id) != 0
    return True

  def __getitem__(self, key):
    """Returns self[key] as a Python primitive value or JavaObject.
    
    Args:
      key: index or key to lookup in this Java object.
    
    Returns:
      `self[i]` as a Python primitive value or JavaObject.
    
    Raises:
      `TypeError` if this isn't a subscriptable Java object.
    """
    if self._is_array():
      return from_java_handle(java_array_index(self.id, key))

    with AutoReleasePool() as auto:
      is_item_getter = from_java_handle(
          java_call_method(ItemGetter_id, Class_isAssignableFrom_id, self.get_class_id()))
      if is_item_getter:
        return from_java_handle(java_call_method(self.id, ItemGetter_getitem_id, auto(to_java_handle(key))))

    raise TypeError(f"object {self.id} is not subscriptable")


class JavaBoundMember:
  """Representation of a Java method reference in Python."""
  def __init__(self, target_class_id: JavaHandle, target, name: str, ref: JavaRef = None, script: JavaObject = None):
    """Member that's bound to a target object, representing a field or method.

    Args:
      target_class_id: Java object ID of enclosing class for this member
      target: either Java object ID of the target through which this member is accessed
      name: name of this member
      ref: JavaRef to manage reference lifetime (optional)
      script: Pyjinn Script object for accessing and calling script functions (optional)
    """
    self.ref = ref
    if ref is not None:
      ref.increment()

    self.target_class_id = target_class_id
    self.target = target
    self.member_name = name
    self.script = script
    if script is not None and name == "getFunction":
      self.member_id = None
      self.script_get_var_as_func = JavaBoundMember(target_class_id, target, "getVariable", ref, script)
      self.script_env = self.script.mainModule().globals()
    else:
      self.member_id = _find_java_member(target_class_id, name)
      self.script_get_var_as_func = None
      self.script_env = None
  
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
    with AutoReleasePool() as auto:
      if self.script_get_var_as_func is None:
        result = java_call_method(self.target, self.member_id,
          *[auto(to_java_handle(a)) for a in args])
        return from_java_handle(result, script_env=self.script_env)
      elif len(args) == 1 and type(args[0]) is str:
        func = self.script_get_var_as_func(args[0])
        def call_func(*params):
          with AutoReleasePool() as auto2:
            args_array = JavaObject(java_new_array(Object_id, *[auto2(to_java_handle(p)) for p in params]))
            return func.call(self.script_env, args_array)
        return call_func
      else:
        raise Exception(f"Script.getFunction() requires 1 str args but got {args}")


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
  
class JavaClassType(JavaObject):
  """JavaObject subclass for Java class objects."""
  def __init__(self, name, class_id):
    super().__init__(class_id)
    self.class_name = name
    self.ctor: JavaHandle = None
    self._is_enum: bool = None
    debug_log(f"Creating class {name} with id {self.id}")

  def __repr__(self):
    return f'JavaClass("{self.class_name}")'

  def is_enum(self):
    """Returns `True` if this class represents a Java enum type."""
    if self._is_enum is None:
      self._is_enum = from_java_handle(java_call_method(self.id, Class_isEnum_id))
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
      valueOf = _find_java_member(self.id, "valueOf")
      with AutoReleasePool() as auto:
        result = JavaObject(java_call_method(_null_id, valueOf, auto(java_string(name))))
        return result

    binding = JavaBoundMember(self.id, _null_id, name)
    if name not in java_field_names(self.id):
      return binding
    try:
      field = java_access_field(self.id, binding.member_id)
      return from_java_handle(field)
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

    with AutoReleasePool() as auto:
      return JavaObject(java_new_instance(self.ctor.id, *[auto(to_java_handle(a)) for a in args]))


def JavaClass(name: str) -> JavaClassType:
  # find_java_class(name) cannot be called from within the JavaClassType constructor because it
  # would leave the JavaClassType in an indeterminate state where required fields like self.ref are
  # not defined and are therefore handled (incorrectly) by JavaClassType.__getattr__.
  class_id = _find_java_class(name)
  return JavaClassType(name, class_id)


null = JavaObject(0)

def callScriptFunction(func_name: str, *args) -> JavaObject:
  """Calls the given Minescript script function.
  
  Args:
    func_name: name of a Minescript script function
    args: args to pass to the given script function
  
  Returns:
    The return value of the given script function as a Python primitive type or JavaObject.
  """
  with AutoReleasePool() as auto:
    return from_java_handle(java_call_script_function(func_name, *[auto(to_java_handle(a)) for a in args]))

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
    return from_java_handle(self.future.wait(timeout=timeout))

def callAsyncScriptFunction(func_name: str, *args) -> JavaFuture:
  """Calls the given Minescript script function asynchronously.
  
  Args:
    func_name: name of a Minescript script function
    args: args to pass to the given script function
  
  Returns:
    `JavaFuture` that will hold the return value of the async funcion when complete.
  """
  with AutoReleasePool() as auto:
    return JavaFuture(java_call_script_function.as_async(func_name, *[auto(to_java_handle(a)) for a in args]))


def _eval_pyjinn_script(script_name: str, script_code: str):
  return (script_name, script_code)

_eval_pyjinn_script = ScriptFunction("eval_pyjinn_script", _eval_pyjinn_script)

def eval_pyjinn_script(script_code: str) -> JavaObject:
  """Creates a Pyjinn script given source code as a string.

  See: [Embedding Pyjinn in Python scripts](pyjinn.md#embedding-pyjinn-in-python-scripts)

  Args:
    script_code: Pyjinn source code

  Returns:
    new Java object of type `org.pyjinn.interpreter.Script`

  Since: v5.0
  """
  return JavaObject(_eval_pyjinn_script("__eval_pyjinn_script__", script_code), is_script=True)

def import_pyjinn_script(pyj_filename: str):
  """Imports a Pyjinn script from a `.pyj` file.

  See: [Embedding Pyjinn in Python scripts](pyjinn.md#embedding-pyjinn-in-python-scripts)

  Args:
    pyj_filename: name a of `.pyj` file containing Pyjinn code to import

  Returns:
    new Java object of type `org.pyjinn.interpreter.Script`

  Since: v5.0
  """
  with open(pyj_filename, 'r', encoding='utf-8') as pyj_file:
    script_code = pyj_file.read()
    return JavaObject(_eval_pyjinn_script(pyj_filename, script_code), is_script=True)