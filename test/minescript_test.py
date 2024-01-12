# SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""minescript_test v4.0 from https://github.com/maxuser0/minescript

Integration testing of script functions in minescript.py.

Requires:
  minescript v4.0

Copy this file to the "minescript" directory within the
"minecraft" directory and run it from within Minecraft with
the command:

\minescript_test

Upon success, "All tests passed" is printed in green to the
chat. Otherwise, a red error message reports the first failure
among the tests.
"""

import asyncio
import minescript
import os
import queue
import re
import sys
import time
import traceback

all_tests = []

current_test_ = ""
chat_listener_ = minescript.ChatEventListener()

class TestFailure(Exception):
  pass

def escape_double_quotes(string):
  return string.replace('"', r'\"')

def print_success(message):
  minescript.chat(
      f'|{{"text":"[{current_test_}] {escape_double_quotes(message)}","color":"green"}}')

def print_failure(message):
  minescript.echo(f'[{current_test_}] {message}')

def expect_true(expr):
  if not expr:
    raise TestFailure(f"Failed expectation: {expr}")

def expect_equal(a, b):
  if a == b:
    print_success(f"Success: {a} == {b}")
  else:
    raise TestFailure(f"Failed equality: {a} != {b}")

def expect_message(message):
  expected_message_re = re.compile(message)
  timeout = time.time() + 1
  #minescript.flush()
  while time.time() < timeout:
    try:
      msg = chat_listener_.get(timeout=0.01)
    except queue.Empty:
      continue
    if expected_message_re.match(msg):
      print_success(f'Found message: {repr(msg)}')
      return True
  raise TestFailure(f'Message not found: {repr(message)}')
  return False

def drain_message_queue():
  try:
    while True:
      chat_listener_.get(block=False)
  except queue.Empty:
    pass


def chat_test():
  minescript.chat("this is a chat message")
  expect_message("<[^>]*> this is a chat message")

all_tests.append(chat_test)


def player_position_test():
  pos = minescript.player_position()
  x, y, z = pos
  x, y, z = int(x), int(y), int(z)
  print_success(f"got position: {x} {y} {z}")

all_tests.append(player_position_test)


def getblock_test():
  block = minescript.getblock(0, 0, 0)
  print_success(f"block at 0 0 0: {repr(block)}")

  x, y, z = minescript.player_position()
  block = minescript.getblock(x, y - 1, z)
  print_success(f"block under player: {repr(block)}")

all_tests.append(getblock_test)


def copy_paste_test():
  filename = os.path.join("minescript", "blockpacks", "test.zip")
  try:
    minescript.execute(r"\copy ~ ~-1 ~ ~5 ~5 ~5 test")
    time.sleep(0.5) # give copy command some time to complete
    expect_true(os.path.isfile(filename))
    expect_message(
        r"Copied volume .* to minescript.blockpacks.test.zip \(.* bytes\)\.")

    minescript.execute(r"\paste ~10 ~-1 ~ this_label_does_not_exist")
    expect_message(r"Error: blockpack file for `this_label_does_not_exist` not found.*")

    minescript.execute(r"\copy 0 0 0 1000 100 1000")
    expect_message("`blockpack_read_world` exceeded soft limit of 1600 chunks")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit")
    expect_message("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit")
    expect_message("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit test")
    expect_message(
        r"Error: copy command requires 6 params of type integer \(plus optional params.*")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit no_limit")
    expect_message("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~10000 ~-1 ~ ~10005 ~5 ~5")
    expect_message("Not all chunks are loaded within the requested `copy` volume")
  finally:
    os.remove(filename)

all_tests.append(copy_paste_test)


def await_loaded_region_test():
  x, y, z = [int(p) for p in minescript.player_position()]
  xoffset = 10000
  zoffset = 10000
  minescript.execute(f"/tp @p {x + xoffset} {y} {z + zoffset}")
  # TODO(maxuser): Support timeout in await_loaded_region(...). Use timeout=5 here.
  minescript.echo(f"Running await_loaded_region(...); should take a few seconds...")
  start_time = time.time()
  loaded = minescript.await_loaded_region(
      x + xoffset, z + zoffset, x + xoffset + 1, z + zoffset + 1)
  try:
    t = time.time() - start_time
    minescript.echo(f"await_loaded_region(...) completed in {t} seconds.")
    expect_true(loaded)
  finally:
    minescript.execute(f"/tp @p {x} {y} {z}")
    minescript.flush()

all_tests.append(await_loaded_region_test)


def player_hand_items_test():
  expect_equal(list, type(minescript.player_hand_items()))

all_tests.append(player_hand_items_test)


def player_inventory_test():
  expect_equal(list, type(minescript.player_inventory()))

all_tests.append(player_inventory_test)


def player_test():
  name = minescript.player_name()
  expect_true(name)

  x, y, z = minescript.player_position()
  yaw, pitch = minescript.player_orientation()

  players = minescript.players()
  players_with_my_name = [
      (p["name"], p["position"], p["yaw"], p["pitch"]) for p in filter(lambda p: p["name"] == name,
      players)]
  expect_equal(players_with_my_name, [(name, [x, y, z], yaw, pitch)])

  entities = minescript.entities()
  entities_with_my_name = [
      (e["name"], e["position"], e["yaw"], e["pitch"]) for e in filter(lambda e: e["name"] == name,
      entities)]
  expect_equal(entities_with_my_name, [(name, [x, y, z], yaw, pitch)])

  # yaw == 0 <-> look +z-axis
  minescript.player_set_orientation(0, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_true(z0 < z1)

  # yaw == 90 <-> look -x-axis
  minescript.player_set_orientation(90, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_true(x0 > x1)

  # yaw == 180 <-> look -z-axis
  minescript.player_set_orientation(180, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_true(z0 > z1)

  # yaw == 270 <-> look +x-axis
  minescript.player_set_orientation(270, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_true(x0 < x1)

all_tests.append(player_test)


def screenshot_test():
  timestamp = int(time.time())
  minescript.screenshot(f"screenshot_test_{timestamp}")
  time.sleep(1) # Give a second for the screenshot to appear.
  filename = os.path.join("screenshots", f"screenshot_test_{timestamp}.png")
  expect_true(os.path.isfile(filename))
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
  expect_true(result is not None)
  expect_equal(len(result), 4)
  expect_equal(len(result[0]), 3)
  expect_equal([type(x) for x in result[0]], [int, int, int])
  expect_equal(type(result[1]), float)
  expect_true(result[1] < max_distance)
  expect_equal(result[2], "up") # We're looking at the "up" side since we're looking down.
  expect_equal(type(result[3]), str)

all_tests.append(player_targeted_block_test)

async def do_async_functions() -> str:
  player_pos, hand_items, inventory = await asyncio.gather(
      minescript.async_player_position(),
      minescript.async_player_hand_items(),
      minescript.async_player_inventory())

  x, y, z = player_pos
  block, blocks, set_pos = await asyncio.gather(
      minescript.async_getblock(x, y - 1, z),
      minescript.async_getblocklist([
          [x - 1, y - 1, z - 1],
          [x - 1, y - 1, z + 1],
          [x + 1, y - 1, z - 1],
          [x + 1, y - 1, z + 1]]),
      minescript.async_player_set_position(x, y + 1, z))

  return f"hand_items={hand_items}, inventory={inventory}"

def do_blocking_functions() -> str:
  player_pos = minescript.player_position()
  hand_items = minescript.player_hand_items()
  inventory = minescript.player_inventory()

  x, y, z = player_pos
  block = minescript.getblock(x, y - 1, z)
  blocks = minescript.getblocklist([
      [x - 1, y - 1, z - 1],
      [x - 1, y - 1, z + 1],
      [x + 1, y - 1, z - 1],
      [x + 1, y - 1, z + 1]])
  minescript.player_set_position(x, y + 1, z)

  return f"hand_items={hand_items}, inventory={inventory}"

def async_function_test():
  start_time = time.time()
  async_result = asyncio.run(do_async_functions())
  async_time = time.time() - start_time

  start_time = time.time()
  blocking_result = do_blocking_functions()
  blocking_time = time.time() - start_time

  expect_equal(async_result, blocking_result)
  expect_true(async_time < blocking_time)
  print_success(f"async_time ({async_time:.4f} sec) < blocking_time ({blocking_time:.4f} sec)")

all_tests.append(async_function_test)


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
    print_success(f"PASSED")
    drain_message_queue()
  except Exception as e:
    print_failure(traceback.format_exc())
    minescript.flush()
    sys.exit(1)

minescript.flush()
current_test_ = "SUCCESS"
print_success(f"All tests passed. ({num_tests_run}/{num_tests_run})")
