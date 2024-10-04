# SPDX-FileCopyrightText: © 2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""draw_text v1 distributed via minescript.net

Script for drawing screen-aligned text.

Customizations of the rendered text include:

- position (x, y)
- color (24-bit hex string, e.g.0xaabbcc)
- scale (default = 4)

Requires:
  minescript v4.0
  lib_java v2

Usage as standalone script:
```
\draw_text TEXT [X Y [HEX_COLOR [SCALE]]]
```

Examples as commands:
```
\draw_text "some text"
\draw_text "Hello, world!" 10 10
\draw_text "green text" 360 10 0x00ff00
\draw_text "hello yellow" 190 100 0xffff00
\draw_text "big red" 200 100 0xff0000 32
```

Example as imported library:
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
"""

from dataclasses import dataclass

from minescript_runtime import JavaException

from minescript import (
  cancel_scheduled_tasks,
  render_loop,
  schedule_render_tasks,
  script_loop,
  version_info,
)

from lib_java import (
  Float,
  JavaClass,
  JavaFloat,
  JavaInt,
  JavaObject,
  JavaString,
  TaskRecorder,
  java_class_map,
  java_member_map,
)

from typing import Callable, List

import sys
import time

versions = version_info()
mc_version = [int(v) for v in versions.minecraft.split(".")]

if versions.minecraft_class_name == "net.minecraft.class_310":
  java_class_map.update({
    "net.minecraft.client.Minecraft": "net.minecraft.class_310",
    "net.minecraft.client.gui.GuiGraphics": "net.minecraft.class_332",
    "com.mojang.blaze3d.vertex.PoseStack": "net.minecraft.class_4587",
    "com.mojang.blaze3d.vertex.VertexSorting": "net.minecraft.class_8251",
  })
  java_member_map.update({
    "getInstance": "method_1551",
    "ON_OSX": "field_1703",
    "gui": "field_1705",
    "getFont": "method_1756",
    "renderBuffers": "method_22940",
    "bufferSource": "method_23000",
    "getWindow": "method_22683",
    "ORTHOGRAPHIC_Z": "field_43361",
    "getWidth": "method_4489",
    "getHeight": "method_4506",
    "drawString": "method_25303",
    "drawCenteredString": "method_25300",
    "pushPose": "method_22903",
    "setIdentity": "method_34426",
    "popPose": "method_22909",
  })
  if mc_version <= [1, 20, 4]:
    java_member_map.update({
      "scale": "method_22905",
      "translate": "method_22904",
    })

Minecraft = JavaClass("net.minecraft.client.Minecraft")

try:
  minecraft = Minecraft.getInstance()
except JavaException as e:
  if versions.mod_loader == "Forge" and mc_version <= [1, 20, 4]:
    # Some versions use an unobfuscated Minecraft class name but obfuscated names otherwise.
    java_member_map["getInstance"] = "m_91087_"
    minecraft = Minecraft.getInstance()
    java_member_map.update({
      "ON_OSX": "f_91002_",
      "gui": "f_91065_",
      "getFont": "m_93082_",
      "renderBuffers": "m_91269_",
      "bufferSource": "m_110104_",
      "getWindow": "m_91268_",
      "ORTHOGRAPHIC_Z": "f_276633_",
      "getWidth": "m_85441_",
      "getHeight": "m_85442_",
      "drawString": "m_280488_",
      "drawCenteredString": "m_280137_",
      "pushPose": "m_85836_",
      "setIdentity": "m_166856_",
      "scale": "m_85841_",
      "translate": "m_252880_",
      "popPose": "m_85849_",
    })
  else:
    raise e

DEFAULT_SCALE = 4

with script_loop:
  Numbers = JavaClass("net.minescript.common.Numbers")

  ON_OSX = Minecraft.ON_OSX
  font = minecraft.gui.getFont()

  GuiGraphics = JavaClass("net.minecraft.client.gui.GuiGraphics")
  with render_loop:
    guiGraphics = GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource())
  window = minecraft.getWindow()

  Matrix4f = JavaClass("org.joml.Matrix4f")
  RenderSystem = JavaClass("com.mojang.blaze3d.systems.RenderSystem")
  VertexSorting = JavaClass("com.mojang.blaze3d.vertex.VertexSorting")
  ORTHOGRAPHIC_Z = VertexSorting.ORTHOGRAPHIC_Z
  matrix4f = Matrix4f().setOrtho(0, window.getWidth(), window.getHeight(), 0, 1000, 3000)

  default_scale = JavaFloat(DEFAULT_SCALE)
  zero = JavaFloat(0)
  one = JavaFloat(1)
  z_translation = JavaFloat(-2000)


use_matrix4f_stack = (mc_version >= [1, 20, 6])

@dataclass
class Text:
  render_func_id: int
  string: JavaObject
  x: JavaObject
  y: JavaObject
  color: JavaObject
  scale: JavaObject

  def __del__(self):
    cancel_scheduled_tasks(self.render_func_id)


def draw_centered_string(text, x, y, color, scale=DEFAULT_SCALE):
  return _draw_string_impl(guiGraphics.drawCenteredString, text, x, y, color, scale)

def draw_string(text, x, y, color, scale=DEFAULT_SCALE):
  return _draw_string_impl(guiGraphics.drawString, text, x, y, color, scale)

def _draw_string_impl(draw_method, text, x, y, color, scale):
  with script_loop:
    text_var = JavaString(text)
    x_var = JavaInt(x)
    y_var = JavaInt(y)
    color_var = JavaInt(color)
    scale_var = JavaFloat(scale)

    task_recorder = TaskRecorder()
    with task_recorder:
      # Adapted from Minecraft::renderFpsMeter
      RenderSystem.clear(256, ON_OSX)
      RenderSystem.setProjectionMatrix(matrix4f, ORTHOGRAPHIC_Z)
      modelViewStack = RenderSystem.getModelViewStack()

      if use_matrix4f_stack:
        modelViewStack.pushMatrix()
      else:
        modelViewStack.pushPose()
        modelViewStack.setIdentity()

      modelViewStack.scale(scale_var, scale_var, one)
      modelViewStack.translate(zero, zero, z_translation)
      RenderSystem.applyModelViewMatrix()
      RenderSystem.lineWidth(one)
      text_scale = Numbers.divide(scale_var, default_scale)
      draw_method(
          font, text_var,
          Numbers.divide(x_var, text_scale).intValue(), Numbers.divide(y_var, text_scale).intValue(),
          color_var)

      if use_matrix4f_stack:
        modelViewStack.popMatrix()
      else:
        modelViewStack.popPose()

      RenderSystem.applyModelViewMatrix()

    render_func_id = schedule_render_tasks(task_recorder.recorded_tasks())
    return Text(render_func_id, text_var, x_var, y_var, color_var, scale_var)


def main():
  args = sys.argv[1:]
  if len(args) == 0:
    raise ValueError(r"Usage: \draw_text TEXT [X Y [HEX_COLOR]]")
  text = args[0]
  x = int(args[1]) if len(args) > 2 else 10
  y = int(args[2]) if len(args) > 2 else 10
  color = int(args[3], 16) if len(args) > 3 else 0xffffff
  scale = float(args[4]) if len(args) > 4 else DEFAULT_SCALE
  async_text = draw_string(text, x, y, color, scale)

  while True:
    time.sleep(60)

if __name__ == "__main__":
  main()

