#!/usr/bin/python3

from typing import List
import sys

# Most characters are 5 dots wide with the exceptions below.
# Values from: https://minecraft.fandom.com/wiki/Language#Font
irregular_char_widths = {
  " ": 3,
  "!": 1,
  '"': 3,
  "'": 1,
  "(": 3,
  ")": 3,
  "*": 3,
  ",": 1,
  ".": 1,
  ":": 1,
  ";": 1,
  "<": 4,
  ">": 4,
  "@": 6,
  "I": 3,
  "[": 3,
  "]": 3,
  "`": 2,
  "f": 4,
  "i": 1,
  "k": 4,
  "l": 2,
  "t": 3,
  "{": 3,
  "|": 1,
  "}": 3,
  "~": 6,
}

def get_char_width(c: str) -> int:
  return irregular_char_widths.get(c, 5)


def resplit(text: str) -> List[str]:
  """Re-splits the given text into lines that fit Minecraft's chat screen.

  The default chat screen width is 295 dots.

  Args:
    text: text to be re-split across line

  Returns:
    list of strings representing lines that fit Minecraft's chat screen
  """
  line_limit = 295
  lines = []
  words = text.split()
  line = ""
  dots_for_line = 0
  dots_for_word = 0
  for word in words:
    dots_for_word = 0
    for ch in word:
      dots_for_word += get_char_width(ch)
    dots_for_word += len(word) - 1  # Reserve dots for spaces.
    dots_for_space = get_char_width(" ") if line else 0
    if dots_for_line + dots_for_space  + dots_for_word <= line_limit:
      if line:
        line += " "
      line += word
      dots_for_line += dots_for_space  + dots_for_word
    else:
      lines.append(line)
      line = word
      dots_for_line = dots_for_word
      dots_for_word = 0
  if line:
    lines.append(line)
  return lines


if __name__ == "__main__":
  print("\n".join(resplit(sys.stdin.read())))
