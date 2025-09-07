# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
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
import java
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
  if element in container:
    print_success(f"Success: {element} in {container}")
  else:
    raise TestFailure(f"Failed expectation: {element} not in {container}")

def expect_does_not_contain(container, element):
  if element in container:
    raise TestFailure(f"Failed expectation: {element} in {container}")
  else:
    print_success(f"Success: {element} not in {container}")

def expect_startswith(string, prefix):
  if string.startswith(prefix):
    print_success(f"Success: {repr(string)} starts with {repr(prefix)}")
  else:
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

def await_script(script_name):
  while True:
    found = False
    for job in minescript.job_info():
      if len(job.command) > 1 and job.command[0] == script_name:
        found = True
        break # continue `while` loop
    if not found:
      return

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

pyjinn_source = r"""
import sys
import atexit

value_set_on_exit = JavaArray((None,))

Minecraft = JavaClass("net.minecraft.client.Minecraft")

pyjinn_dict = dict(x="foo", y="bar")
pyjinn_list = [1, 2, 3]
pyjinn_tuple = (1, 2, 3)
pyjinn_str = "This is a test."

java_list = JavaList(pyjinn_list)
java_array = JavaArray(pyjinn_tuple)

def get_fps() -> int:
  return Minecraft.getInstance().getFps()

def get_player_name() -> str:
  return Minecraft.getInstance().player.getName().getString()

def get_num_jobs() -> int:
  return len(job_info())

def set_value_on_exit(value):
  global value_set_on_exit
  value_set_on_exit[0] = value

atexit.register(set_value_on_exit, value="assigned!")

def cancel_exit_handler():
  atexit.unregister(set_value_on_exit)
"""

@test
def pyjinn_test():
  script = java.eval_pyjinn_script(pyjinn_source)
  value_set_on_exit = script.getVariable("value_set_on_exit")

  get_fps = script.getFunction("get_fps")
  fps = get_fps()
  expect_equal(type(fps), int)
  expect_gt(fps, 0)
  expect_lt(fps, 1000)
  
  get_player_name = script.getFunction("get_player_name")
  expect_equal(get_player_name(), minescript.player_name())

  with minescript.script_loop:
    pyjinn_dict = script.getVariable("pyjinn_dict")
    pyjinn_list = script.getVariable("pyjinn_list")
    pyjinn_tuple = script.getVariable("pyjinn_tuple")
    pyjinn_str = script.getVariable("pyjinn_str")
    expect_equal(3, len(pyjinn_list))
    expect_equal(3, len(pyjinn_tuple))
    expect_equal("This is a test.", pyjinn_str)
    expect_equal("foo", pyjinn_dict["x"])
    expect_equal("bar", pyjinn_dict["y"])

    java_list = script.getVariable("java_list")
    java_array = script.getVariable("java_array")
    expect_equal(3, len(java_list))
    expect_equal(3, len(java_array))

    for i, elem in enumerate(pyjinn_list):
      expect_equal(i + 1, elem)
    for i, elem in enumerate(pyjinn_tuple):
      expect_equal(i + 1, elem)
    for i, elem in enumerate(java_list):
      expect_equal(i + 1, elem)
    for i, elem in enumerate(java_array):
      expect_equal(i + 1, elem)
    
    for i in range(3):
      expect_equal(i + 1, pyjinn_list[i])
    
    for i in range(3):
      expect_equal(i + 1, pyjinn_tuple[i])

    expect_contains(pyjinn_list, 1)
    expect_contains(pyjinn_tuple, 1)
    expect_contains(java_list, 1)
    expect_contains(java_array, 1)

    expect_does_not_contain(pyjinn_list, 4)
    expect_does_not_contain(pyjinn_tuple, 4)
    expect_does_not_contain(java_list, 4)
    expect_does_not_contain(java_array, 4)
  
  get_num_jobs = script.getFunction("get_num_jobs")
  expect_equal(get_num_jobs(), len(minescript.job_info()))

  script.exit()
  expect_equal("assigned!", value_set_on_exit[0])

  # Re-run the script, but now with the at-exit handler canceled.
  script = java.eval_pyjinn_script(pyjinn_source)
  value_set_on_exit = script.getVariable("value_set_on_exit")
  cancel_exit_handler = script.getFunction("cancel_exit_handler")
  cancel_exit_handler()
  script.exit()
  expect_equal(None, value_set_on_exit[0])



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
    await_script("copy")
    expect_true(os.path.isfile(filename))
    expect_message(
        r"Copied volume .* to minescript.blockpacks.test.zip \(.* bytes\)\.")

    minescript.execute(r"\paste ~10 ~-1 ~ this_label_does_not_exist")
    await_script("paste")
    expect_message(r"Error: blockpack file for `this_label_does_not_exist` not found.*")

    minescript.execute(r"\copy 0 0 0 1000 100 1000")
    await_script("copy")
    expect_message("`blockpack_read_world` exceeded soft limit of 1600 chunks")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit")
    await_script("copy")
    expect_message("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit")
    await_script("copy")
    expect_message("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 test no_limit test")
    await_script("copy")
    expect_message(
        r"Error: copy command requires 6 params of type integer \(plus optional params.*")

    minescript.execute(r"\copy ~ ~ ~ ~1000 ~100 ~1000 no_limit no_limit")
    await_script("copy")
    expect_message("Not all chunks are loaded within the requested `copy` volume")

    minescript.execute(r"\copy ~10000 ~-1 ~ ~10005 ~5 ~5")
    await_script("copy")
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
  expect_equal(minescript.HandItems, type(minescript.player_hand_items()))


@test
def player_inventory_test():
  expect_equal(list, type(minescript.player_inventory()))


@test
def player_test():
  name = minescript.player_name()
  x, y, z = minescript.player_position()
  yaw, pitch = minescript.player_orientation()
  players = minescript.players(nbt=True)
  entities = minescript.entities(nbt=True)

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
  minescript.execute(r"""\eval 'print("this is " + "a test")' 2>null""")
  await_script("eval")
  expect_message("this is a test")

  minescript.execute(r'''\eval "print('this is ' + 'another test')" 2>null''')
  await_script("eval")
  expect_message("this is another test")

  minescript.execute(r"""\eval 'print(\'this is \' + \'an escaped test\')' 2>null""")
  await_script("eval")
  expect_message("this is an escaped test")

  minescript.execute(r'''\eval "print(\"this is \" + \"a doubly escaped test\")" 2>null''')
  await_script("eval")
  expect_message("this is a doubly escaped test")


@test
def java_functions_test():
  JavaHandle = minescript.JavaHandle
  object_class = minescript.java_class("java.lang.Object")
  class_class = minescript.java_class("java.lang.Class")
  object_getClass = minescript.java_member(object_class, "getClass")
  class_getName = minescript.java_member(class_class, "getName")
  Numbers_class = minescript.java_class("net.minescript.common.Numbers")
  Numbers_divide = minescript.java_member(Numbers_class, "divide")
  Numbers_lessThan = minescript.java_member(Numbers_class, "lessThan")

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

  value, type_ = do_operator(Numbers_lessThan, minescript.java_int(22), minescript.java_float(7))
  expect_equal(value, "false")
  expect_equal(type_, "java.lang.Boolean")

  value, type_ = do_operator(Numbers_lessThan, minescript.java_int(7), minescript.java_float(22))
  expect_equal(value, "true")
  expect_equal(type_, "java.lang.Boolean")

@test
def java_library_test():
  Minescript = java.JavaClass("net.minescript.common.Minescript")
  expect_equal(type(Minescript), java.JavaClassType)
  expect_equal(type(Minescript.mappingsLoader), java.JavaObject)

  Minecraft = java.JavaClass("net.minecraft.client.Minecraft")
  expect_equal(type(Minecraft.getInstance().getFps()), int)

  player_name = minescript.player_name()
  java_player_name = Minecraft.getInstance().player.getName().getString()
  expect_equal(type(java_player_name), str)
  expect_equal(java_player_name, player_name)


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
