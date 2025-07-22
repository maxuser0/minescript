## Pyjinn

### Contents

- [What is Pyjinn?](#what-is-pyjinn)
- [Pyjinn in Minescript 5.0](#pyjinn-in-minescript-50)
- [Java Integration](#java-integration)
- [Python Language Features](#python-language-features)
- [Python 3.x features not supported by Pyjinn](#python-3x-features-not-supported-by-pyjinn)

### What is Pyjinn?

**Pyjinn** (pronounced like "pidgeon") is a scripting language with Python syntax
that integrates deeply with Java programs. It’s a "pidgin" language that looks
and feels like Python while its implementation and runtime behavior are based in
Java. (The name "Pyjinn" is a portmanteau of "Python" and "jinn", which is
another word for "genie".)

While Pyjinn does not support the Python standard library, Pyjinn scripts can
access the Java standard library and any publicly accessible Java classes loaded
into the Java program in which it's embedded.


### Pyjinn in Minescript 5.0

Pyjinn is integrated into the upcoming release of Minescript 5.0.  This allows
Python-syntax scripts to be executed directly by the Minescript mod within
Minecraft's Java process without requiring a separate Python installation.
Externally executed Python scripts continue to be supported. Minescript Python
scripts and Minescript Pyjinn scripts share the same
[script APIs](https://minescript.net/docs/#minescript-module), with the
exception of event handlers. Minescript Python scripts manage their own
event queue using [EventQueue](https://minescript.net/docs/#eventqueue), whereas
Minescript Pyjinn scripts use single-threaded event handling inspired by
JavaScript:

- `add_event_listener(event_type: str, callback: Callable[..., None], **args) -> int`
- `set_interval(callback: Callable[..., None], timer_millis: int, *args) -> int`
- `set_timeout(callback: Callable[..., None], timer_millis: int, *args) -> int`
- `remove_event_listener(listener_id: int) -> bool`

The supported event types are:

- "tick", "render", "key", "mouse", "chat", "outgoing_chat_intercept", "add_entity",
  "block_update", "explosion", "take_item", "damage", "chunk"

Scripts can import the Minescript standard library explicitly. For simple IDE integration (e.g.
VSCode), you can use imports like these:

- `from system.pyj.minescript import *`
- `import system.pyj.minescript as m`

For consistency with existing Python scripts you can use imports like these:

- `from minescript import *`
- `import minescript`
- `import minescript as m`

If there are no imports of `minescript` or `system.pyj.minescript` in the main
script, it is imported implicitly as:

- `from system.pyj.minescript import *`

For IDE compatibility, `.pyj` files are used only for the main script file and
not imported from other scripts. This is because IDEs like VSCode do not
recognize imports of `.pyj` files. To enable Python-syntax support (syntax
highlighting, autocompletion, etc), add this line to the top of your `.pyj`
file:

- `#!python`

Pyjinn's search path for imports (the equivalent of `PYTHONPATH`, but currently hardcoded) is:

- `minecraft/minescript`
- `minecraft/minescript/system/pyj`

Pyjinn libraries in your `minecraft/minescript` directory can be imported by dropping their `.py`
filename extension.  E.g. `minecraft/minescript/my_kewl_library.py` can be imported into a Pyjinn
script as:

```
import my_kewl_library
```

Pyjinn scripts (`.pyj`) and Python scripts (`.py`) can import the same `.py`
library if that library is compatible with both Python and Pyjinn.

Imported `.py` libraries can detect if they're running in a Pyjinn context by checking whether
`sys.version` contains the string `"Pyjinn"`.  E.g. to disallow Python scripts from importing a
Pyjinn-only `.py` library, you can add code like this at the top of your library:

```
import sys

if "Pyjinn" not in sys.version:
  raise ImportError(f"Module '{__name__}' requires a Pyjinn interpreter.")
```

### Java Integration

Import Java classes with `JavaClass()` which takes a string literal.
Java classes are resolved at script parse time, not script execution time.

```
List = JavaClass("java.util.List")
```

`JavaClass` is Pyjinn's wrapper around a Java `Class<?>` and provides access to the class's
constructors and static methods using function-call and method-call syntax. This is different from
`Class<?>` instances which are metaclasses that provide access to reflection information.

```
print(JavaClass("java.lang.String"))
# prints: JavaClass("java.lang.String")

print(type("This is a string"))
# prints: JavaClass("java.lang.String")

print("This is a string".getClass())
# prints Class<String>: class java.lang.String

print(type(42))
# prints: JavaClass("java.lang.Integer")

print(type(42).MAX_VALUE)
# prints equivalent of `Integer.MAX_VALUE`: 2147483647

print(type(42)(99))
# prints equivalent of `new Integer(99)`: 99
```

Call static method of Java class:
```
java_list = List.of(1, 2, 3)
```

Call method of Java object:
```
print(java_list.size())
```

Get Java List from Pyjinn list (does not make a copy):
```
[1, 2, 3].getJavaList()
```

Get Java array from Pyjinn tuple (does not make a copy):
```
(1, 2, 3).getJavaArray()
```

Java arrays and iterables are automatically treated as Pyjinn sequences:
```
java_array = (1, 2, 3).getJavaArray()  # type of java_array is Object[]
for x in java_array:
  print(x)

# Applies Python slice syntax to Object[]:
print(java_array[-2:])
```

Pyjinn functions passed to methods with a parameter type that's a Java interface are automatically
converted to implementations of that interface. In this example, `Stream::map` is a Java method that
takes a `Function<>` and `Stream::filter` takes a `Predicate<>`. Here, Pyjinn `lambda` expressions
are passed to those Java methods to square the input number and filter for the resulting values that
are even numbers:
```
java_list = [x for x in range(10)].getJavaList()
print(
    java_list.stream()
        .map(lambda x: x * x)
        .filter(lambda x: x % 2 == 0)
        .toList())
# prints: [0, 4, 16, 36, 64]
```

Pyjinn functions can also be converted manually to a Java interface by calling the Java interface as
if it's a Python constructor:
```
Runnable = JavaClass("java.lang.Runnable")
x = Runnable(lambda: print("hello!"))
x.run()  # prints: hello!
```

### Python Language Features

The following are the Python language features supported in Pyjinn.

Names in `UPPER_CASE_WITH_UNDERSCORES` are example names.

`...` refers to omitted code.

**Python expressions:**

```
# Constant expressions:
None  # NoneType
True  # bool
False # bool
123   # int
3.14  # float
"hi"  # str
'bye' # str
r'\o/' # str (raw literal)
"""triple quoted"""
'''triple quoted'''

# Formatted strings (f-string):
X = 1
Y = 2
print(f"{X} + {Y} = {X+Y}")  # prints: 1 + 2 = 3

# Tuple literal:
(X, Y, Z)

# Tuple assignment:
X, Y = 1, 2
X, Y = (1, 2)
X, Y = [1, 2]

# List literal:
[X, Y, Z]

# Dict literal:
{K1: V1, K2: V2, ...}

# Binary operators:
X is Y
X is not Y
X == Y
X < Y
X <= Y
X > Y
X >= Y
X != Y
X in Y
X not in Y

# Call function with an iterable sequence into distinct args:
X = [1, 2, 3]
FUNC(*X)  # call as FUNC(1, 2, 3)

# Operators:
-X  # for numeric types
X and Y
X or Y
not X
X + Y
X - Y
X * Y
X / Y
X ** Y
X % Y

# Index/Slice operations:
X[FROM]
X[FROM:]
X[:TO]
X[:]
X[FROM:TO:STEP]
X[:-1]  # Negative indices are relative to the end of the sequence.

# if-else expression:
TRUE_VALUE if CONDITION else FALSE_VALUE

# List comprehension:
[X * 2 for X in range(5)]  # evaluates to: [0, 2, 4, 6, 8]
[C for C in "hello"]  # evaluates to: ["h", "e", "l", "l", "o"]

# Lambda expression:
lambda: print("no args")
lambda X: print("1 arg:", X)
lambda X, Y: print("2 args:", X, Y)
```


**Python statements:**

```
# Imports:
import MODULE
import MODULE as ALIAS
from MODULE import NAME1 as ALIAS1, NAME2 as ALIAS2, ...
from MODULE import *

# Assignment:
VAR = VALUE
VAR += VALUE
VAR -= VALUE
VAR *= VALUE
VAR /= VALUE

# Deletion:
del OBJECT
del DICT[KEY]
del LIST[INDEX]

# Print to stdout:
print("SOME STRING")

# Print to stderr:
print("SOME STRING", file=sys.stderr)

# Function definitions:
def FUNCTION_NAME(ARG1, ...):
  ...
  return VALUE

def FUNCTION_NAME(ARG1, *VAR_ARG):
  ...

def FUNCTION_NAME(ARG1, KEYWORD1=VALUE1, KEYWORD2=VALUE2):
  ...

# Class definitions:
class CLASS_NAME:
  def __init__(self, ...):
    self.X = VALUE
    self.Y = VALUE
    ...

  @classmethod
  def METHOD(CLASS, ...):
    ...

  @staticmethod
  def METHOD(...):
    ...

# Construct instance of a class:
OBJECT = CLASS_NAME(...)

# Reference field of object:
print(OBJECT.X)

# Mutable dataclass:
@dataclass
class CLASS_NAME:
  FIELD1: TYPE
  FIELD2: TYPE = DEFAULT_VALUE
  ...

# Immutable dataclass:
@dataclass(frozen=True)
class CLASS_NAME:
  ...

if CONDITION:
  ...
elif CONDITION:
  ...
else:
  ...

for VAR in ITERABLE:
  ...
  if CONDITION:
    break
  ...
  if CONDITION:
    continue

while CONDITION:
  ...
  if CONDITION:
    break
  ...
  if CONDITION:
    continue

try:
  ...
except EXCEPTION as NAME:
  ...
except:
  pass

raise EXCEPTION(...)

def FUNCTION_NAME(...):
  global GLOBAL_VAR
  VAR = ...
  def INNER_FUNCTION(...):
    nonlocal VAR
    ...
```

### Python 3.x features not supported by Pyjinn

Unsupported language features in Pyjinn 0.5:

- Python standard library (except for some basics in `sys` module: `sys.argv`,
  `sys.version`, `sys.stdout`, `sys.stderr`)
- some `str` methods (strings in Pyjinn are `java.lang.String` augmented with
  these common methods: `startswith()`, `endswith()`, and `join()`)
- `**kwargs` on caller side and callee side of a function call (but individual keyword args are
  supported, e.g. `print("foo", file=sys.stderr)`)
- `dict(k1=v1, k2=v2, ...)` syntax for constructing a dictionary (but `{k1: v1, k2: v2, ...}` is
  supported)
- threading
- inheritance
- `with` statement
- `match` statement (Python 3.10+)
- keyword-only arguments to functions ([PEP 3102](https://peps.python.org/pep-3102/))
- f-string assignment expressions, e.g. `f"{x = }"` (just prints x's value, not `"x = "`)
- step increments in slice expressions (parses ok, but step values that aren't equivalent to 1 result in an exception)
- special class methods with double underscores (except for `__init__()` which is
  supported)
- arbitrary-precision integers (only fixed-size integers like Java `Integer` and `Long`
  are supported)
- user-defined decorators
- asyncio and async/await syntax
- generators and `yield` statement
- Python-style metaclasses

