# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""timer v1 distributed via minescript.net

Sends a message or executes a command at the specified time.

Requires:
  minescript v2.0

Usage:
  \timer <time> (chat|echo|execute|eval) <messageOrCommand>

<time> can be a countdown in hours, minutes, or seconds or a
time of day. Supported formats include: `1:23am`, `1:23 PM`,
`13:23`, `10s` (seconds), `5m` (minutes), `2h` (hours). Add `*` to
countdown times to repeat, e.g. `5m*` repeats every 5 minutes.
Add `*` and a number to repeat that number of times at the
given time interval.

Examples:
  Send a chat at 12 noon:
  \timer 12pm chat "FYI: it is now noon"

  Send a message to yourself at 12:30:
  \timer 12:30pm echo "note to self: time to eat lunch"

  Copy blocks labeled "timed_copy" in 2 minutes:
  \timer 2m execute  "\\copy ~ ~ ~ ~64 ~64 ~64 timed_copy"

  Set game time to midday every hour:
  \timer 1h* execute "/time set day"

  Take 3 screenshots 5 seconds apart:
  \timer 5s*3 eval "screenshot()"
  (note: screenshot() added in Minescript v2.1)
"""

from datetime import datetime
import builtins
import minescript
import re
import sys
import time

try:
  import eval as eval_script
  eval_supported = True
except ImportError:
  eval_supported = False


class TimeOfDayTimer:
  def __init__(self, hour: int, minute: int):
    """
    Args:
      hour: hour of the day in 24-hour format
      minute: minute within the hour
    """
    self.hour = hour
    self.minute = minute

  def wait(self) -> bool:
    "Wait until the given time of day. Return False to indicate this is a one-time event."
    current_time = datetime.now()
    current_seconds = current_time.hour * 3600 + current_time.minute * 60 + current_time.second
    finish_seconds = self.hour * 3600 + self.minute * 60
    time.sleep(finish_seconds - current_seconds)
    return False  # do not repeat


def parse_time_of_day(time_spec: str):
  time_of_day_re = re.compile(r"([0-9]{,2}):([0-9]{2}) *(am|pm|AM|PM)?")
  m = time_of_day_re.fullmatch(time_spec)
  if m:
    hour = int(m.group(1))
    minute = int(m.group(2))
    am_pm = m.group(3).lower()
    if am_pm == "am" and hour == 12:
      hour = 0
    elif am_pm == "pm" and hour != 12:
      hour += 12

    if hour > 23:
      raise ValueError(f'Hour exceeds 11pm / 23:00: "{time_spec}"')

    return TimeOfDayTimer(hour, minute)

  return None


class CountdownTimer:
  def __init__(self, hours: int, minutes: int, seconds: int, repeat_count: int):
    """
    Args:
      hours, minutes, seconds: time to wait before timer goes off
      repeat_count: how many times to trigger the timer
    """
    self.hours = hours
    self.minutes = minutes
    self.seconds = seconds
    self.repeat_count = repeat_count

  def wait(self) -> bool:
    "Wait until the given time expires. Return True until wait() is called `repeat_count` times."
    time.sleep(self.hours * 3600 + self.minutes * 60  + self.seconds)
    self.repeat_count -= 1
    return self.repeat_count > 0


def parse_countdown(time_spec: str):
  countdown_re = re.compile(r"([0-9]+)([hms])(\*([0-9]+)?)?")
  m = countdown_re.fullmatch(time_spec)
  hours, minutes, seconds = 0, 0, 0
  repeat_count = 1
  if m:
    value = int(m.group(1))
    unit = m.group(2)
    if unit == "h":
      hours = value
    elif unit == "m":
      minutes = value
    elif unit == "s":
      seconds = value

    if m.group(4):
      repeat_count = int(m.group(4))
    elif m.group(3):
      repeat_count = sys.maxsize  # Not infinite, but good enough.

    return CountdownTimer(hours, minutes, seconds, repeat_count)

  return None


def run(time_spec: str, action: str, param: str):
  """Perform an action after the specified time has passed.

  Args:
    time_spec: specification of time, either relative (e.g. "15m") or absolute ("1:23pm");
      supported formats include: "1:23pm", "13:23", "10s" (seconds), "5m" (minutes), "2h" (hours);
      add "*" to relative time formats to repeat, e.g. "5m*" repeats every 5 minutes;
      add "*" and a number to repeat that number of times at the given time interval.
    action: one of "chat", "echo", "execute", or "eval" ("eval" requires install of eval.py)
    param: parameter to pass to the given action, either a chat message or command/code to execute
  """
  timer = parse_time_of_day(time_spec) or parse_countdown(time_spec)
  if not timer:
    raise ValueError(f'Unable to parse time spec: "{time_spec}"')

  if action == "chat":
    func = lambda: minescript.chat(param)
  elif action == "echo":
    func = lambda: minescript.echo(param)
  elif action == "execute":
    func = lambda: minescript.execute(param)
  elif action == "eval":
    if not eval_supported:
      minescript.echo(
          "timer.py: `eval` requires installation of `eval.py` "
          "(available at https://minescript.net/downloads/)")
      return
    func = lambda: eval_script.run(param)

  while True:
    repeat = timer.wait()
    func()
    if not repeat:
      break


if __name__ == "__main__":
  if len(sys.argv) != 4:
    minescript.echo(
        f"timer.py: Expected 1 parameter but got {len(sys.argv) - 1}: {sys.argv[1:]}")
    minescript.echo(r"Usage: \timer <time> (chat|echo|execute|eval) <messageOrCommand>")
    sys.exit(1)

  run(sys.argv[1], sys.argv[2], sys.argv[3])
