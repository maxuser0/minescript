### lib_java v2

Library for using Java reflection from Python, wrapping
the low-level Java API script functions (`java_*`).

*Requires:*

- `minescript v4.0`

*Example:*

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

#### AutoReleasePool.\_\_call\_\_
*Usage:* <code>AutoReleasePool.\_\_call\_\_(ref: JavaHandle) -> JavaHandle</code>

Track `ref` for auto-release when this pool is deleted or goes out of scope.

*Returns:*

- `ref` for convenient wrapping of functions returning a JavaHandle.


#### TaskRecorder
Context for recording tasks when interacting with JavaObject fields and methods.

*Example:*

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


#### TaskRecorder.active
*Usage:* <code>@staticmethod TaskRecorder.active() -> "TaskRecorder"</code>

Returns the active [`TaskRecorder`](#taskrecorder) for the current thread, or `None`.

#### TaskRecorder.\_\_bool\_\_
*Usage:* <code>TaskRecorder.\_\_bool\_\_()</code>

Always `True` as a convenience for checking [`TaskRecorder.active`](#taskrecorderactive) which may be `None`

#### Float
Wrapper class for mirroring Java `float` in Python.

Python `float` maps to Java `double`, and Python doesn't have a built-in single-precision float.


#### JavaObject
Python representation of a Java object.

#### JavaObject.\_\_init\_\_
*Usage:* <code>JavaObject(target_id: JavaHandle, ref: JavaRef = None)</code>

Constructs a Python handle to a Java object given a `JavaHandle`. 

#### JavaObject.toString
*Usage:* <code>JavaObject.toString() -> str</code>

Returns a `str` representation of `this.toString()` from Java.

#### JavaObject.set_value
*Usage:* <code>JavaObject.set_value(value: Any)</code>

Sets this JavaObject to reference `value` instead.

`value` can be any of the following types:
- bool: converted to Java Boolean
- int: converted to Java Integer
- Float: converted to Java Float
- float: converted to Java Double
- str: converted to Java String
- JavaObject: this JavaObject will reference the same Java object as `value`


#### JavaObject.\_\_getattr\_\_
*Usage:* <code>JavaObject.\_\_getattr\_\_(name: str)</code>

Accesses the field or method named `name`.

*Args:*

- `name`: name of a field or method on this JavaObject's class

*Returns:*

- If `name` matches a field on this JavaObject's class, then return the
  value of that field as a Python primitive or new JavaObject. Otherwise
  return a [`JavaBoundMember`](#javaboundmember) equivalent to the Java expression
  `this::methodName`.


#### JavaObject.\_\_len\_\_
*Usage:* <code>JavaObject.\_\_len\_\_() -> int</code>

If this JavaObject represents a Java array, returns the length of the array.

Raises `TypeError` if this isn't an array.


#### JavaObject.\_\_getitem\_\_
*Usage:* <code>JavaObject.\_\_getitem\_\_(i: int)</code>

If this JavaObject represents a Java array, returns `array[i]`.

*Args:*

- `i`: index into array from which to get an element

*Returns:*

- `array[i]` as a Python primitive value or JavaObject.

*Raises:*

  `TypeError` if this isn't an array.


#### JavaBoundMember
Representation of a Java method reference in Python.

#### JavaBoundMember.\_\_init\_\_
*Usage:* <code>JavaBoundMember(target_class_id: JavaHandle, target, name: str, ref: JavaRef = None)</code>

Member that's bound to a target object, representing a field or method.

*Args:*

- `target_class_id`: Java object ID of enclosing class for this member
- `target`: either Java object ID of the target through which this member is accessed, or
      Task for scheduled execution
- `name`: name of this member


#### JavaBoundMember.\_\_call\_\_
*Usage:* <code>JavaBoundMember.\_\_call\_\_(\*args)</code>

Calls the bound method with the given `args`.

*Returns:*

- A Python primitive (bool, int, float, str) if applicable, otherwise a JavaObject.


#### JavaInt
JavaObject subclass for Java Integer.

#### JavaFloat
JavaObject subclass for Java Float.

#### JavaString
JavaObject subclass for Java String.

#### JavaClass
JavaObject subclass for Java class objects.

#### JavaClass.is_enum
*Usage:* <code>JavaClass.is_enum()</code>

Returns `True` if this class represents a Java enum type.

#### JavaClass.\_\_getattr\_\_
*Usage:* <code>JavaClass.\_\_getattr\_\_(name)</code>

Accesses the static field or static method named `name` on this Java class.

*Args:*

- `name`: name of a static field or static method on this Java class.

*Returns:*

- If `name` matches a static field on this Java class, then return the value of that field as
  a new JavaObject. Otherwise return a [`JavaBoundMember`](#javaboundmember) equivalent to the Java expression
  `ThisClass::staticMethodName`.


#### JavaClass.\_\_call\_\_
*Usage:* <code>JavaClass.\_\_call\_\_(\*args)</code>

Calls the constructor for this Java class that takes the given `args`, if applicable.

*Returns:*

- JavaObject representing the newly constructed Java object.


#### callScriptFunction
*Usage:* <code>callScriptFunction(func_name: str, \*args) -> [JavaObject](#javaobject)</code>

Calls the given Minescript script function.

*Args:*

- `func_name`: name of a Minescript script function
- `args`: args to pass to the given script function

*Returns:*

- The return value of the given script function as a Python primitive type or JavaObject.


#### JavaFuture
Java value that will become available in the future when an async function completes.

#### JavaFuture.wait
*Usage:* <code>JavaFuture.wait(timeout=None)</code>

Waits for the async function to complete.

*Args:*

- `timeout`: if not `None`, timeout in seconds to wait on the async function to complete

*Returns:*

- Python primitive value or JavaObject returned from the async function upon completion.


#### callAsyncScriptFunction
*Usage:* <code>callAsyncScriptFunction(func_name: str, \*args) -> [JavaFuture](#javafuture)</code>

Calls the given Minescript script function asynchronously.

*Args:*

- `func_name`: name of a Minescript script function
- `args`: args to pass to the given script function

*Returns:*

- [`JavaFuture`](#javafuture) that will hold the return value of the async funcion when complete.


