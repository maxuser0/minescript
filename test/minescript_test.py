# SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""minescript_test v3.1 from https://github.com/maxuser0/minescript

Integration testing of script functions in minescript.py.

Requires:
  minescript v3.0

Copy this file to the "minescript" directory within the
"minecraft" directory and run it from within Minecraft with
the command:

\minescript_test

Upon success, "All tests passed" is printed in green to the
chat. Otherwise, a red error message reports the first failure
among the tests.
"""

import minescript
import os
import re
import sys
import time
import threading
import traceback

all_tests = []

current_test_ = ""
message_lock_ = threading.Lock()
messages_ = []

class TestFailure(Exception):
  pass

def EscapeDoubleQuotes(string):
  return string.replace('"', r'\"')

def PrintSuccess(message):
  minescript.chat(f'|{{"text":"[{current_test_}] {EscapeDoubleQuotes(message)}","color":"green"}}')

def PrintFailure(message):
  minescript.echo(f'[{current_test_}] {message}')

def ExpectTrue(expr):
  if not expr:
    raise TestFailure(f"Failed expectation: {expr}")

def ExpectEqual(a, b):
  if a == b:
    PrintSuccess(f"Success: {a} == {b}")
  else:
    raise TestFailure(f"Failed equality: {a} != {b}")

def ExpectMessage(message):
  global messages_
  expected_message_re = re.compile(message)
  timeout = time.time() + 1
  while time.time() < timeout:
    minescript.flush()
    message_lock_.acquire()
    old_messages = messages_
    messages_ = []
    message_lock_.release()
    for msg in old_messages:
      if expected_message_re.match(msg):
        PrintSuccess(f'Found message: {repr(msg)}')
        return True
    time.sleep(0.1)
  raise TestFailure(f'Message not found: {repr(message)}')
  return False

def ChatCallback(message):
  message_lock_.acquire()
  messages_.append(message)
  message_lock_.release()


def chat_test():
  minescript.chat("this is a chat message")
  ExpectMessage("<[^>]*> this is a chat message")

all_tests.append(chat_test)


def player_position_test():
  pos = minescript.player_position()
  x, y, z = pos
  x, y, z = int(x), int(y), int(z)
  PrintSuccess(f"got position: {x} {y} {z}")

all_tests.append(player_position_test)


def getblock_test():
  block = minescript.getblock(0, 0, 0)
  PrintSuccess(f"block at 0 0 0: {repr(block)}")

  x, y, z = minescript.player_position()
  block = minescript.getblock(x, y - 1, z)
  PrintSuccess(f"block under player: {repr(block)}")

all_tests.append(getblock_test)


def copy_paste_test():
  filename = os.path.join("minescript", "blockpacks", "test.zip")
  try:
    minescript.execute(r"\copy ~ ~-1 ~ ~5 ~5 ~5 test")
    time.sleep(0.5) # give copy command some time to complete
    ExpectTrue(os.path.isfile(filename))
    ExpectMessage(
        r"Copied volume .* to minescript.blockpacks.test.zip \(.* bytes\)\.")

    minescript.execute(r"\paste ~10 ~-1 ~ this_label_does_not_exist")
    ExpectMessage(r"Error: blockpack file for `this_label_does_not_exist` not found.*")

    minescript.execute(r"\copy 0 0 0 1000 100 1000")
    ExpectMessage("`blockpack_read_world` exceeded soft limit of 1600 chunks")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit")
    ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit")
    ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit test")
    ExpectMessage(
        r"Error: copy command requires 6 params of type integer \(plus optional params.*")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit no_limit")
    ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~10000 ~-1 ~ ~10005 ~5 ~5")
    ExpectMessage("Not all chunks are loaded within the requested `copy` volume")
  finally:
    os.remove(filename)

all_tests.append(copy_paste_test)


def await_loaded_region_test():
  chunk_loaded = []
  lock = threading.Lock()
  def region_loaded(ok):
    chunk_loaded.append(ok)
    lock.release()

  x, y, z = [int(p) for p in minescript.player_position()]
  lock.acquire()
  xoffset = 10000
  zoffset = 10000
  minescript.await_loaded_region(
      x + xoffset, z + zoffset, x + xoffset + 1, z + zoffset + 1, region_loaded)
  minescript.execute(f"/tp @p {x + xoffset} {y} {z + zoffset}")
  lock.acquire(timeout=5)  # seconds
  try:
    ExpectTrue(chunk_loaded)
    ExpectTrue(chunk_loaded[0])
  finally:
    minescript.execute(f"/tp @p {x} {y} {z}")
    minescript.flush()

all_tests.append(await_loaded_region_test)


def player_hand_items_test():
  ExpectEqual(list, type(minescript.player_hand_items()))

all_tests.append(player_hand_items_test)


def player_inventory_test():
  ExpectEqual(list, type(minescript.player_inventory()))

all_tests.append(player_inventory_test)


def player_test():
  name = minescript.player_name()
  ExpectTrue(name)

  x, y, z = minescript.player_position()
  yaw, pitch = minescript.player_orientation()

  players = minescript.players()
  players_with_my_name = [
      (p["name"], p["position"], p["yaw"], p["pitch"]) for p in filter(lambda p: p["name"] == name,
      players)]
  ExpectEqual(players_with_my_name, [(name, [x, y, z], yaw, pitch)])

  entities = minescript.entities()
  entities_with_my_name = [
      (e["name"], e["position"], e["yaw"], e["pitch"]) for e in filter(lambda e: e["name"] == name,
      entities)]
  ExpectEqual(entities_with_my_name, [(name, [x, y, z], yaw, pitch)])

  # yaw == 0 <-> look +z-axis
  minescript.player_set_orientation(0, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  ExpectTrue(z0 < z1)

  # yaw == 90 <-> look -x-axis
  minescript.player_set_orientation(90, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  ExpectTrue(x0 > x1)

  # yaw == 180 <-> look -z-axis
  minescript.player_set_orientation(180, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  ExpectTrue(z0 > z1)

  # yaw == 270 <-> look +x-axis
  minescript.player_set_orientation(270, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  ExpectTrue(x0 < x1)

all_tests.append(player_test)


def screenshot_test():
  timestamp = int(time.time())
  minescript.screenshot(f"screenshot_test_{timestamp}")
  time.sleep(1) # Give a second for the screenshot to appear.
  filename = os.path.join("screenshots", f"screenshot_test_{timestamp}.png")
  ExpectTrue(os.path.isfile(filename))
  os.remove(filename)

all_tests.append(screenshot_test)


def player_targeted_block_test():
  # Record player orientation then look down for the targeted block test since player is likely to
  # have ground beneath them. Lastly, restore player's original orientation.
  yaw, pitch = minescript.player_orientation()
  minescript.player_set_orientation(yaw, 90)
  max_distance = 400
  result = minescript.player_get_targeted_block(max_distance)
  minescript.player_set_orientation(yaw, pitch)
  ExpectTrue(result is not None)
  ExpectEqual(len(result), 4)
  ExpectEqual(len(result[0]), 3)
  ExpectEqual([type(x) for x in result[0]], [int, int, int])
  ExpectEqual(type(result[1]), float)
  ExpectTrue(result[1] < max_distance)
  ExpectEqual(result[2], "up") # We're looking at the "up" side since we're looking down.
  ExpectEqual(type(result[3]), str)

all_tests.append(player_targeted_block_test)

if "--list" in sys.argv[1:]:
  for test in all_tests:
    minescript.chat(f'|{{"text":"{test.__name__}","color":"green"}}')
  sys.exit(0)

explicit_tests = set()

# Assume all explicitly listed tests should be run (and tests not listed should not be run), unless
# `--exclude` is an arg in which case all explicitly listed tests should be skipped (and tests not
# listed should be run).
should_include = True
for arg in sys.argv[1:]:
  if arg == "--exclude":
    should_include = False
  else:
    explicit_tests.add(arg)

minescript.register_chat_message_listener(ChatCallback)
num_tests_run = 0
for test in all_tests:
  current_test_ = test.__name__
  if explicit_tests:
    if not should_include and current_test_ in explicit_tests:
      continue
    if should_include and current_test_ not in explicit_tests:
      continue
  num_tests_run += 1
  try:
    test()
    minescript.flush()
    PrintSuccess(f"PASSED")
    messages_ = []
  except Exception as e:
    PrintFailure(traceback.format_exc())
    minescript.flush()
    sys.exit(1)

minescript.flush()
current_test_ = "SUCCESS"
PrintSuccess(f"All tests passed. ({num_tests_run}/{num_tests_run})")
