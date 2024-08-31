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

import minescript
import os
import queue
import re
import sys
import time
import traceback

all_tests = []

current_test_ = ""

event_queue = minescript.EventQueue()
event_queue.register_chat_listener()

class TestFailure(Exception):
  pass

def escape_double_quotes(string):
  return string.replace('"', r'\"')

def print_success(message):
  minescript.echo_json(
      { "text": f"[{current_test_}] {escape_double_quotes(message)}", "color": "green" })

def print_failure(message):
  minescript.echo(f'[{current_test_}] {message}')

def expect_true(expr):
  if not expr:
    raise TestFailure(f"Failed expectation: {expr}")

def expect_false(expr):
  if expr:
    raise TestFailure(f"Failed expectation: not {expr}")

def expect_contains(container, element):
  if element not in container:
    raise TestFailure(f"Failed expectation: {element} not in {container}")

def expect_startswith(string, prefix):
  if not string.startswith(prefix):
    raise TestFailure(f"Failed expectation: {repr(string)} does not start with {repr(prefix)}")

def expect_equal(a, b):
  if a == b:
    print_success(f"Success: {a} == {b}")
  else:
    raise TestFailure(f"Failed equality: {a} != {b}")

def expect_gt(a, b):
  if a > b:
    print_success(f"Success: {a} > {b}")
  else:
    raise TestFailure(f"Failed comparison: {a} <= {b} (expected >)")

def expect_lt(a, b):
  if a < b:
    print_success(f"Success: {a} < {b}")
  else:
    raise TestFailure(f"Failed comparison: {a} >= {b} (expected <)")

def expect_message(message):
  expected_message_re = re.compile(message)
  timeout = time.time() + 1
  #minescript.flush()
  while time.time() < timeout:
    try:
      event = event_queue.get(timeout=0.01)
    except queue.Empty:
      continue
    msg = event.message
    if expected_message_re.match(msg):
      print_success(f'Found message: {repr(msg)}')
      return True
  raise TestFailure(f'Message not found: {repr(message)}')
  return False

def drain_message_queue():
  try:
    while True:
      event_queue.get(block=False)
  except queue.Empty:
    pass

def test(test_func):
  all_tests.append(test_func)
  return test_func


# BEGIN TESTS

@test
def chat_test():
  minescript.chat("this is a chat message")
  expect_message("<[^>]*> this is a chat message")


@test
def player_position_test():
  pos = minescript.player_position()
  x, y, z = pos
  x, y, z = int(x), int(y), int(z)
  print_success(f"got position: {x} {y} {z}")


@test
def getblock_test():
  block = minescript.getblock(0, 0, 0)
  print_success(f"block at 0 0 0: {repr(block)}")

  x, y, z = minescript.player_position()
  block = minescript.getblock(x, y - 1, z)
  print_success(f"block under player: {repr(block)}")


@test
def copy_paste_test():
  filename = os.path.join("minescript", "blockpacks", "test.zip")
  try:
    minescript.execute(r"\copy ~ ~-1 ~ ~5 ~5 ~5 test")
    time.sleep(1) # give copy command some time to complete
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
    if os.path.isfile(filename):
      os.remove(filename)


@test
def blockpack_test():
  pos1 = [int(p) for p in minescript.player_position()]
  pos2 = [p + 50 for p in pos1]
  blockpack = minescript.BlockPack.read_world(
      pos1, pos2, comments={"hello": "world", "foo": "bar"})
  comments = blockpack.comments()
  expect_equal({"hello": "world", "foo": "bar"}, comments)


@test
def await_loaded_region_test():
  x, y, z = [int(p) for p in minescript.player_position()]
  xoffset = 10000
  zoffset = 10000
  minescript.execute(f"/tp @p {x + xoffset} {y} {z + zoffset}")
  minescript.echo(f"Running await_loaded_region(...) with 5 second timeout")
  start_time = time.time()
  try:
    loaded = minescript.await_loaded_region.as_async(
        x + xoffset, z + zoffset, x + xoffset + 1, z + zoffset + 1).wait(timeout=5)
  except TimeoutError:
    pass

  try:
    t = time.time() - start_time
    minescript.echo(f"await_loaded_region(...) completed in {t} seconds.")
    expect_true(loaded)
  finally:
    minescript.execute(f"/tp @p {x} {y} {z}")
    minescript.flush()

  try:
    minescript.await_loaded_region.as_async(999990, 999990, 999999, 999999).wait(timeout=0.01)
    loaded = True
  except TimeoutError:
    loaded = False
  expect_false(loaded)


@test
def player_hand_items_test():
  try:
    minescript.options.legacy_dict_return_values = True
    expect_equal(list, type(minescript.player_hand_items()))
  finally:
    minescript.options.legacy_dict_return_values = False

  expect_equal(minescript.HandItems, type(minescript.player_hand_items()))


@test
def player_inventory_test():
  expect_equal(list, type(minescript.player_inventory()))


@test
def player_test():
  # run_tasks(...) guarantees that all tasks are executed on the same game tick.
  tasks = [
      minescript.player_name.as_task(),
      minescript.player_position.as_task(),
      minescript.player_orientation.as_task(),
      minescript.players.as_task(nbt=True),
      minescript.entities.as_task(nbt=True)
  ]
  tasks.append(minescript.Task.as_list(*tasks))
  name, (x, y, z), (yaw, pitch), players, entities = minescript.run_tasks(tasks)

  # Task.as_list() leaves entity data as raw dicts rather than auto-converting them to EntityData.
  # So convert to EntityData manually.
  players = [minescript.EntityData(**p) for p in players]
  entities = [minescript.EntityData(**e) for e in entities]

  players_with_my_name = [
      (p.name, p.position, p.yaw, p.pitch) for p in filter(lambda p: p.name == name,
      players)]
  expect_equal(players_with_my_name, [(name, [x, y, z], yaw, pitch)])

  entities_with_my_name = [
      (e.name, e.position, e.yaw, e.pitch)
      for e in filter(lambda e: e.name == name, entities)]
  expect_equal(entities_with_my_name, [(name, [x, y, z], yaw, pitch)])

  # yaw == 0 <-> look +z-axis
  minescript.player_set_orientation(0, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_lt(z0, z1)

  # yaw == 90 <-> look -x-axis
  minescript.player_set_orientation(90, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_gt(x0, x1)

  # yaw == 180 <-> look -z-axis
  minescript.player_set_orientation(180, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_gt(z0, z1)

  # yaw == 270 <-> look +x-axis
  minescript.player_set_orientation(270, 0)
  x0, y0, z0 = minescript.player_position()
  minescript.player_press_forward(True)
  time.sleep(0.5)
  minescript.player_press_forward(False)
  minescript.flush()
  x1, y1, z1 = minescript.player_position()
  expect_true(x0 < x1)


@test
def screenshot_test():
  timestamp = int(time.time())
  minescript.screenshot(f"screenshot_test_{timestamp}")
  time.sleep(1) # Give a second for the screenshot to appear.
  filename = os.path.join("screenshots", f"screenshot_test_{timestamp}.png")
  expect_true(os.path.isfile(filename))
  os.remove(filename)


@test
def player_targeted_block_test():
  # Record player orientation then look down for the targeted block test since player is likely to
  # have ground beneath them. Lastly, restore player's original orientation.
  yaw, pitch = minescript.player_orientation()
  minescript.player_set_orientation(yaw, 90)
  max_distance = 400
  result = minescript.player_get_targeted_block(max_distance)
  minescript.player_set_orientation(yaw, pitch)
  expect_true(result is not None)
  expect_equal(len(result[0]), 3)
  expect_equal([type(x) for x in result[0]], [int, int, int])
  expect_equal(type(result[1]), float)
  expect_true(result[1] < max_distance)
  expect_equal(result[2], "up") # We're looking at the "up" side since we're looking down.
  expect_equal(type(result[3]), str)

def do_async_functions() -> str:
  player_pos, hand_items, inventory = [x.wait() for x in [
      minescript.player_position.as_async(),
      minescript.player_hand_items.as_async(),
      minescript.player_inventory.as_async()]]

  x, y, z = player_pos
  block, blocks = [x.wait() for x in [
      minescript.getblock.as_async(x, y - 1, z),
      minescript.getblocklist.as_async([
          [x - 1, y - 1, z - 1],
          [x - 1, y - 1, z + 1],
          [x + 1, y - 1, z - 1],
          [x + 1, y - 1, z + 1]])
      ]
  ]

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

  return f"hand_items={hand_items}, inventory={inventory}"

@test
def async_function_test():
  start_time = time.time()
  async_result = do_async_functions()
  async_time = time.time() - start_time

  start_time = time.time()
  blocking_result = do_blocking_functions()
  blocking_time = time.time() - start_time

  expect_equal(async_result, blocking_result)
  expect_true(async_time < blocking_time)
  print_success(f"async_time ({async_time:.4f} sec) < blocking_time ({blocking_time:.4f} sec)")


@test
def player_health_test():
  health = minescript.player_health()
  expect_equal(float, type(health))
  expect_gt(health, 0)


@test
def player_look_at_test():
  # Save original orientation.
  yaw, pitch = minescript.player_orientation()

  # Look in the +x direction.
  pos = [int(p) for p in minescript.player_position()]
  pos[0] += 1
  pos[1] += 1
  minescript.player_look_at(*pos)

  # Restore original orientation.
  minescript.player_set_orientation(yaw, pitch)


@test
def screen_name_test():
  # Assume that no GUI screen is active while test is running.
  name = minescript.screen_name()
  expect_equal(None, name)


@test
def world_info_test():
  info = minescript.world_info()
  expect_equal(len(info.__dict__), 9)


@test
def command_parse_test():
  def await_eval_script():
    while True:
      found = False
      for job in minescript.job_info():
        if len(job.command) > 1 and job.command[0] == "eval" and "print" in job.command[1]:
          found = True
          break # continue `while` loop
      if not found:
        return

  minescript.execute(r"""\eval 'print("this is " + "a test")' 2>null""")
  await_eval_script()
  expect_message("this is a test")

  minescript.execute(r'''\eval "print('this is ' + 'another test')" 2>null''')
  await_eval_script()
  expect_message("this is another test")

  minescript.execute(r"""\eval 'print(\'this is \' + \'an escaped test\')' 2>null""")
  await_eval_script()
  expect_message("this is an escaped test")

  minescript.execute(r'''\eval "print(\"this is \" + \"a doubly escaped test\")" 2>null''')
  await_eval_script()
  expect_message("this is a doubly escaped test")


@test
def java_test():
  JavaHandle = minescript.JavaHandle
  object_class = minescript.java_class("java.lang.Object")
  class_class = minescript.java_class("java.lang.Class")
  object_getClass = minescript.java_member(object_class, "getClass")
  class_getName = minescript.java_member(class_class, "getName")
  Numbers_class = minescript.java_class("net.minescript.common.Numbers")
  Numbers_divide = minescript.java_member(Numbers_class, "divide")

  def get_java_class_name(value: JavaHandle) -> str:
    value_class = minescript.java_call_method(value, object_getClass)
    class_name = minescript.java_call_method(value_class, class_getName)
    return minescript.java_to_string(class_name)

  def do_operator(op: JavaHandle, x: JavaHandle, y: JavaHandle) -> JavaHandle:
    result = minescript.java_call_method(minescript.java_null, op, x, y)
    return minescript.java_to_string(result), get_java_class_name(result)

  value, type_ = do_operator(Numbers_divide, minescript.java_int(22), minescript.java_int(7))
  expect_equal(value, "3")
  expect_equal(type_, "java.lang.Integer")

  value, type_ = do_operator(Numbers_divide, minescript.java_double(22), minescript.java_int(7))
  expect_startswith(value, "3.14")
  expect_equal(type_, "java.lang.Double")

  value, type_ = do_operator(Numbers_divide, minescript.java_int(22), minescript.java_float(7))
  expect_startswith(value, "3.14")
  expect_equal(type_, "java.lang.Float")


# END TESTS


if "--list" in sys.argv[1:]:
  for test in all_tests:
    minescript.echo_json({ "text": test.__name__, "color": "green" })
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
    print_success(f"PASSED")
    drain_message_queue()
    minescript.flush()
    time.sleep(0.1)
  except Exception as e:
    print_failure(traceback.format_exc())
    minescript.flush()
    sys.exit(1)

minescript.flush()
current_test_ = "SUCCESS"
print_success(f"All tests passed. ({num_tests_run}/{num_tests_run})")
