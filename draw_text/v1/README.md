### draw_text v1

Script for drawing screen-aligned text.

Customizations of the rendered text include:

- position (x, y)
- color (24-bit hex string, e.g.0xaabbcc)
- scale (default = 4)

*Requires:*

- `minescript v4.0`
- `lib_java v2`

Usage as standalone script:
```
\draw_text TEXT [X Y [HEX_COLOR [SCALE]]]
```


*Examples as commands:*

```
\draw_text "some text"
\draw_text "Hello, world!" 10 10
\draw_text "green text" 360 10 0x00ff00
\draw_text "hello yellow" 190 100 0xffff00
\draw_text "big red" 200 100 0xff0000 32
```


*Example as imported library:*

```
from draw_text import (draw_string, draw_centered_string)
import time
text = draw_string("some white text", x=20, y=20, color=0xffffff, scale=4)
time.sleep(5)
text.x.set_value(25)  # Move text to the right.
time.sleep(1)
text.y.set_value(25)  # Move text down.
time.sleep(1)
text.color.set_value(0xff0000)  # Change text color to red.
text.string.set_value("now it's red")  # Change text string.
time.sleep(2)
```

