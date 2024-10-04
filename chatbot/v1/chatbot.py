# SPDX-FileCopyrightText: © 2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""chatbot v1 distributed via minescript.net

Requires:
  minescript v3.1
  lib_nbt v1

Usage:
  Prompt chatbot to get a single response and exit:

    \chatbot PROMPT

  Run chatbot in "interactive mode" in the background and
  have it respond to messages that match the regular
  expression PATTERN, with options to ignore upper/lower
  case and give the chatbot a name:

    \chatbot -i PATTERN [ignorecase] [name=NAME]

  In interactive mode, chatbot output is prefixed with `>>>`
  and the bot can be stopped by entering `quitbot` into the
  chat.

Examples:
  Ask chatbot a question and get a single response:

    \chatbot "Which entities are approaching me?"

  Run chatbot interactively, responding to chat messages
  that include the phrase "bot," with any combination of
  upper/lower case:

    \chatbot -i ".*\bbot,\s" ignorecase
"""

import json
import lib_nbt
import minescript
import os
import requests
import re
import sys
import time

from threading import Lock

try:
  import openai_api_key
except ImportError:
  echo("OpenAI API key is missing.")
  echo("Create the file `openai_api_key.py` in the `minescript`")
  echo("directory and add a global variable named `SECRET_KEY`:")
  echo("")
  echo('SECRET_KEY = "sk-..."')
  echo("")
  echo("Set global variable to an API key that you create at:")
  echo("https://beta.openai.com/account/api-keys")
  sys.exit(1)

from minescript import echo, BlockPos
from typing import Any, List, Set, Dict, Tuple, Optional, Callable

OPENAI_API_URL = "https://api.openai.com/v1/completions"
OPENAI_API_HEADERS = { "Authorization": f"Bearer {openai_api_key.SECRET_KEY}" }


def ask_chatgpt(prompt: str, model: str = "text-davinci-003", max_tokens: int = 150) -> str:
  data = {
    "model": model,
    "prompt": prompt,
    "max_tokens": max_tokens
  }

  response = requests.post(OPENAI_API_URL, headers=OPENAI_API_HEADERS, json=data)
  if response.status_code != 200:
    raise Exception(f"Response code not ok ({response.status_code}): {response.text}")

  choices = response.json().get("choices")
  if choices is None:
    raise Exception(f"No `choices` field in response: {response.text}")

  answer = choices[0]["text"].strip()
  return answer


def simple_block_name(block: str) -> str:
  return block.replace("minecraft:", "").replace("_", " ").split("[")[0]


def block_pos(pos: Tuple[float, float, float]) -> BlockPos:
  p = [0] * 3
  for i in range(3):
    if pos[i] < 0:
      p[i] = int(pos[i]) - 1
    else:
      p[i] = int(pos[i])
  p[1] -= 1
  return tuple(p)


def block_pos_minus_1y(pos: BlockPos) -> BlockPos:
  return (pos[0], pos[1] - 1, pos[2])


def distance_squared(pos1: BlockPos, pos2: BlockPos) -> float:
  return (pos1[0] - pos2[0]) ** 2 + (pos1[1] - pos2[1]) ** 2 + (pos1[2] - pos2[2]) ** 2


def query(question: str, debug: bool = False) -> str:
  context = (
    "me: This is tab-delimited tabular data representing Minecraft entity's name, " +
    "type, health, current activity, block they're on, and position:\n")

  world_props = minescript.world_properties()
  entities = minescript.entities(nbt=True)
  my_pos = None
  for e in entities:
    if "local" in e:
      my_pos = e["position"]
      break

  if not my_pos:
    return f"Cannot find local player in entities: {entities}"

  # Assign "dsqr" to each entity: distance squared to local player.
  for e in entities:
    e["dsqr"] = distance_squared(my_pos, e["position"])

  # Truncate the list of entities at the 50 closest to the local player.
  entities.sort(key=lambda e: e["dsqr"])
  entities = entities[:50]

  # Get the block types for the 3 blocks at and immediately below each entity.
  positions: List[BlockPos] = [None] * (3 * len(entities))
  for i, e in enumerate(entities):
    positions[3 * i] = block_pos(e["position"])
    positions[3 * i + 1] = block_pos_minus_1y(positions[3 * i])
    positions[3 * i + 2] = block_pos_minus_1y(positions[3 * i + 1])
  blocks: List[str] = [simple_block_name(b) for b in minescript.getblocklist(positions)]

  my_name = None
  my_targeting = None

  for i, entity in enumerate(entities):
    me: bool = entity.get("local") or False
    snbt = entity.get("nbt")
    nbt = lib_nbt.parse_snbt(snbt) if snbt else {}
    name = entity["name"]
    #name = json.loads(nbt.get("CustomName", "{}")).get("text") or entity["name"]
    if me:
      my_name = name
    health = entity.get("health") or "none"
    if type(health) is float:
      health = str(health).rstrip(".0")
    type_ = entity["type"].split(".")[-1].replace("_", " ")
    pos = [round(p) for p in entity["position"]]
    velocity = entity["velocity"]
    if velocity[1] < 0 and velocity[1] > -0.08:
      #echo(f'(entity["{name}"].velocity[1] = {velocity[1]} -> 0)')
      velocity[1] = 0  # ignore gravitational effect on y velocity
    on_ground = nbt.get("OnGround") == 1
    sleeping = nbt.get("Sleeping") == 1
    sitting = nbt.get("Sitting") == 1
    falling = nbt.get("FallDistance") not in (None, 0)
    variant = nbt.get("variant")
    if type(variant) is str:
      variant = variant.split(":")[-1].replace("_", " ")
      type_ = f"{variant} {type_}"

    if sleeping:
      activity = "sleep"
    elif sitting:
      activity = "sit"
    elif on_ground and not sitting:
      activity = "stand"
    elif falling:
      activity = "fall"
    else:
      activity = "float"

    # Find the first of the 3 vertically stacked blocks for this entity that's not air, if any.
    block = "air"
    for dy in range(3):
      b = blocks[3 * i + dy]
      if b != "air":
        block = b
        break

    context += f"{name}\t{type_}\t{health}\t{activity}\t{block}\t{' '.join([str(p) for p in pos])}\n"

    if me:
      target = minescript.player_get_targeted_block(500)
      if target:
        _, distance, face, block = target
        distance = round(distance * 10) / 10
        if face == "up":
          face = "top"
        elif face == "down":
          face = "bottom"
        block = simple_block_name(block)
        if not block.endswith(" block"):
          block += " block"
        my_targeting = f"targeting the {face} face of a {block} {distance} blocks away"
      else:
        my_targeting = f"targeting the sky"

  context += "\n"
  if my_name:
    context += f"me: My name is {my_name} and I'm {my_targeting}."

  spawn = world_props["spawn"]
  weather = "clear"
  if world_props["raining"]:
    weather = "raining"
  elif world_props["thundering"]:
    weather = "thundering"

  def ticks_to_time(ticks: int) -> str:
    seconds = ticks // 20
    if seconds < 120:
      return f"{seconds} seconds"
    minutes = seconds // 60
    if minutes < 60:
      return f"{minutes} minutes {seconds % 60} seconds"
    hours = minutes // 60
    return f"{hours} hours {minutes % 60} minutes"

  def time_until_sunrise(ticks: int) -> str:
    return ticks_to_time((23000 - ticks) % 24000) + " until sunrise"

  def time_until_sunset(ticks: int) -> str:
    return ticks_to_time((12000 - ticks) % 24000) + " until sunset"

  day_ticks = world_props["day_ticks"] % 24000
  sun_times = f"{time_until_sunrise(day_ticks)}, {time_until_sunset(day_ticks)}"
  if day_ticks < 5000:
    day_time = f"morning"
  elif day_ticks < 7000:
    day_time = f"around noon"
  elif day_ticks < 9000:
    day_time = f"afternoon"
  elif day_ticks < 12000:
    day_time = f"late afternoon"
  elif day_ticks < 13000:
    day_time = f"sunset"
  elif day_ticks < 22000:
    day_time = f"night"
  else:
    day_time = f"sunrise"

  context += f"\nme: World properties: spawn location is {spawn[0]} {spawn[1]} {spawn[2]}, "
  context += f"weather is {weather}, time is {day_time}, "
  context += f"{time_until_sunrise(day_ticks)}, {time_until_sunset(day_ticks)}."

  context += f"\nme: {question}\n\nyou:"
  if debug:
    echo(context)
  answer = ask_chatgpt(context)
  return answer


interactive_bot_lock = Lock()
quit_interactive_bot = False
interactive_bot_trigger_re = None # pattern in chat messags that trigger the bot
interactive_bot_name = None
message_queue = []

def on_chat_received(message):
  global quit_interactive_bot
  global interactive_bot_name
  global interactive_bot_trigger_re

  if quit_interactive_bot:
    return
  if type(message) == str:
    if ">>>" in message:
      return # Assume these are messages sent by chatbot itself.

    with interactive_bot_lock:
      if "quitbot" in message:
        quit_interactive_bot = True
      elif interactive_bot_trigger_re.match(message):
        if interactive_bot_name is not None:
          message_queue.append(f'Your name is "{interactive_bot_name}". {message}')
        else:
          message_queue.append(message)


def run_interactive_bot_loop(trigger_pattern: str, re_flags):
  global quit_interactive_bot
  global interactive_bot_trigger_re
  global message_queue

  minescript.log(f'chatbot trigger pattern: "{trigger_pattern}"')
  interactive_bot_trigger_re = re.compile(trigger_pattern, flags=re_flags)
  minescript.register_chat_message_listener(on_chat_received)

  echo(f">>> {interactive_bot_name or 'chatbot'} is listening. Quit by typing `quitbot`.")

  should_quit = False
  while not should_quit:
    time.sleep(0.5)

    message = None
    with interactive_bot_lock:
      should_quit = quit_interactive_bot
      if message_queue:
        message = message_queue.pop(0)

    if message:
      echo(f">>> ({interactive_bot_name or 'chatbot'} is answering...)")
      echo(f">>> {query(message.strip())}")

  minescript.unregister_chat_message_listener()
  time.sleep(0.5)


if __name__ == "__main__":
  if len(sys.argv) == 1:
    import help
    import sys
    docstr = help.ReadDocString("chatbot.py")
    if docstr:
      print(docstr, file=sys.stderr)
      sys.exit(0)
    sys.exit(1)

  if sys.argv[1] == "-i":
    re_flags = 0
    for arg in sys.argv[3:]:
      if arg == "ignorecase":
        re_flags = re.IGNORECASE
      elif arg.startswith("name="):
        interactive_bot_name = arg.split("=", 1)[1]
      else:
        echo(f"Unexpected arg: {arg}")
        sys.exit(1)
    run_interactive_bot_loop(sys.argv[2], re_flags)
  else:
    answer = query(sys.argv[1], debug=len(sys.argv) > 2).strip()
    if answer:
      if answer.startswith("/") or answer.split()[0] in (
          "execute", "summon", "teleport", "tp", "give"):
        echo(f"Running command: `{answer}`")
        minescript.execute(answer)
      else:
        echo(answer)
    else:
      echo("Sorry, ChatGPT is unavailable at the moment. Try again soon.")

