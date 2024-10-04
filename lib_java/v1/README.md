## `lib_java v1`

Library for using Java reflection from Python, wrapping
the low-level Java API provided via `java_*` script functions.

&nbsp;

**Requirements**

  Minescript v4.0 or higher

**Example**

```
from minescript import echo
from lib_java import JavaClass

# This example requires a version of Minecraft
# with unobfuscated symbols, like dev-mode launchers
# or NeoForge.
Minecraft = JavaClass("net.minecraft.client.Minecraft")
minecraft = Minecraft.getInstance()
echo("fps:", minecraft.getFps())
```
