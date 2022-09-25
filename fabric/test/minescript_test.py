# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""minescript_test v2.1 from https://github.com/maxuser0/minescript

Integration testing of script functions in minescript.py.

Requires:
  minescript v2.1

Copy this file to the "minescript" directory within the
"minecraft" directory and run it from within Minecraft with
the command:

\minescript_test

Upon success, "All tests passed" is printed in green to the
chat. Otherwise, a red error message reports the first failure
among the tests.
"""

import minescript
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
minescript.register_chat_message_listener(ChatCallback)


def ChatTest():
  minescript.chat("this is a chat message")
  ExpectMessage("<[^>]*> this is a chat message")

all_tests.append(ChatTest)


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
  minescript.execute(r"\copy ~ ~-1 ~ ~5 ~5 ~5 test")
  ExpectMessage(r"Copied [0-9]+ blocks\.")

  minescript.execute(r"\paste ~10 ~-1 ~ this_label_does_not_exist")
  # TODO(maxuser): Improve this error message so it's more user friendly.
  ExpectMessage(r"FileNotFoundError: .* No such file or directory: .*this_label_does_not_exist")

  minescript.execute(r"\copy 0 0 0 1000 100 1000")
  ExpectMessage("`copy` command exceeded soft limit")

  minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit")
  ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

  minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit")
  ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

  minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit test")
  ExpectMessage(r"Expected 6 params of type integer \(plus optional params.*")

  minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit no_limit")
  ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

  minescript.execute(r"\copy ~10000 ~-1 ~ ~10005 ~5 ~5")
  ExpectMessage("Not all chunks are loaded within the requested `copy` volume")

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


# TODO(maxuser): Add test for minescript.screenshot().


if len(sys.argv) == 1:
  for test in all_tests:
    current_test_ = test.__name__
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
num_tests = len(all_tests)
current_test_ = "SUCCESS"
PrintSuccess(f"All tests passed. ({num_tests}/{num_tests})")
