# Changelog

## Docs

- For Pyjinn integration with Java and language compatibility with Python see
  [minescript.net/pyjinn](https://minescript.net/pyjinn)
- To set up Fabric mappings for Pyjinn scripts see
  [minescript.net/mappings](https://minescript.net/mappings)

## Minescript 5.0b3

- Define zombie callback handler for Pyjinn scripts ([d088278](https://github.com/maxuser0/minescript/commit/d0882788c1a5850a546e14a817f1727ab4c02dc1))
- Fix bug in Pyjinn commands with quotes on Windows ([7698fea](https://github.com/maxuser0/minescript/commit/7698fea14ef8a23bcd3a686ed2233652ac81f5e9))
- Support BlockPack, BlockPacker from Pyjinn scripts ([e37cb05](https://github.com/maxuser0/minescript/commit/e37cb053559608ccaee19e92c60e29d310d2e589))
- Pass script filename for errors from parser ([30f93c9](https://github.com/maxuser0/minescript/commit/30f93c9c6584a48ae43291b98bd32cd5cc7d38a1))
- Fix bug in outgoing chat interceptor with no args ([f10419b](https://github.com/maxuser0/minescript/commit/f10419bc71cea1a04d4efb882d79cf89c842176e))
- Fix bug in eval.pyj for Windows that causes errors ([241f0cc](https://github.com/maxuser0/minescript/commit/241f0cc001d2d10045844fec74de350c1ac0895b))
- Update Minescript to 5.0b3, Pyjinn to 0.7 ([7f8cec5](https://github.com/maxuser0/minescript/commit/7f8cec5642867867813a4ba4a77cefe842b6bcfc))

### Pyjinn 0.7

- Force class loading on eval of JavaClass() ([7c52a81](https://github.com/maxuser0/pyjinn/commit/7c52a81f4bf40807f8053e68932ec1bf8210e01b))
- Support zombie callback handler for exited scripts ([b51d949](https://github.com/maxuser0/pyjinn/commit/b51d949e62e3f0a353f947425a079dfe574905d2))
- Make PyTuple and PyList streamable with stream() ([5a5fa7a](https://github.com/maxuser0/pyjinn/commit/5a5fa7a4b5864ec863cd52eb0f67675a9b271b00))
- Output filename, line num for Pyjinn syntax errors ([7e4c292](https://github.com/maxuser0/pyjinn/commit/7e4c2921167bc0dcd36c2038e89e5dc24f7fadaa))
- Load class from JavaClass() at eval time ([da1aff3](https://github.com/maxuser0/pyjinn/commit/da1aff35e210d80fbf3f099cc3d3d99f26ef426f))
- Update Pyjinn version to 0.7 ([cf7f5ac](https://github.com/maxuser0/pyjinn/commit/cf7f5ac61856d80136e8a01f60bd1df48a041bb6))


## Minescript 5.0b2

- Improve errors when Pyjinn can't find method/ctor ([e88a241](https://github.com/maxuser0/minescript/commit/e88a2419f7f7c315b5e0dbbd609e65ca64429158))
- Improve error message when Python can't be found ([c1ef53d](https://github.com/maxuser0/minescript/commit/c1ef53d3fe9b46f9962cc47b02b7c0979bef5a60))
- Better message when Pyjinn script throws exception ([a05ec73](https://github.com/maxuser0/minescript/commit/a05ec73b3f06d857b85ef86d32685260b3c4e86a))
- Fix bug in method mappings for Fabric ([4035ecc](https://github.com/maxuser0/minescript/commit/4035eccddfbfda1cb5901ba4612b97e34375095b))
- Update Minescript version to 5.0b2, Pyjinn to 0.6 ([6e76d2f](https://github.com/maxuser0/minescript/commit/6e76d2fd26da96b5d47fc4df33d713f77bbf16a0))

### Pyjinn 0.6

- Support << and >> bit-shift ops and hex() function ([8a85253](https://github.com/maxuser0/pyjinn/commit/8a852537e0d66e503e53b46c5ae36215e0e841d4))
- Improve error messages when no method/ctor found ([df61558](https://github.com/maxuser0/pyjinn/commit/df61558f7bb695084a37abbf1c711999577cff7f))
- Add debug logging to method selection ([4b12854](https://github.com/maxuser0/pyjinn/commit/4b12854d20e69ef0bb0d78a5ab309c1b7f7430d7))
- Update Pyjinn version to 0.6 ([151cefe](https://github.com/maxuser0/pyjinn/commit/151cefeb941a71697c02338e4cbc6e01006b1de4))


## Minescript 5.0b1

- Pyjinn API change in method type checker ([d583429](https://github.com/maxuser0/minescript/commit/d583429280e3becf367ac4bb7faa7f4a755fcc32))
- Support Outer.Nested class syntax with mappings ([268f8b1](https://github.com/maxuser0/minescript/commit/268f8b16b8709c2dcb09aeed7948b6796324ce3c))
- Fix mappings for nested classes and enums ([0732f77](https://github.com/maxuser0/minescript/commit/0732f775526fcf64958b39e204d0f8ee76c1fc55))
- Unify use of mappings across Python and Pyjinn ([c8e909d](https://github.com/maxuser0/minescript/commit/c8e909d5a9c428bf6e7dd7980bf7ba7a03607305))
- Fix minescript.pyj to use valid Python syntax ([782c5a2](https://github.com/maxuser0/minescript/commit/782c5a2882c05a6f413e4d6354c9b45b858555f9))
- Support passing non-JSON objects to Pyjinn events ([8abfa8f](https://github.com/maxuser0/minescript/commit/8abfa8ff9358d1c193b8434e1dcd61d544acbaf0))
- Make `__script__.vars["game"]` threadsafe ([394c25a](https://github.com/maxuser0/minescript/commit/394c25a49ec72b76b0ef71108945bf19ca9789b4))
- Support "world" event for connect/disconnect ([0c2d139](https://github.com/maxuser0/minescript/commit/0c2d1392eb5b600c1c50aa6f63d2511c12a18a6f))
- Refactor event handling into EventDispatcher class ([6e63e6b](https://github.com/maxuser0/minescript/commit/6e63e6ba0da758ca540a3403f730827415a1d0f4))
- Normalize event listener registration logic ([3325e66](https://github.com/maxuser0/minescript/commit/3325e662edbda7d8eda91ea57b36be73d4e86492))
- Provide global game state shared by Pyjinn scripts ([06dffbc](https://github.com/maxuser0/minescript/commit/06dffbcc014be0bd074c43c681f18ead987042ea))

### Pyjinn 0.5

- Fix method resolution to check superclasses ([81d840a](https://github.com/maxuser0/pyjinn/commit/81d840aac4dd928a9145662b61eb5ff87f2dc836))
- Remove java.vendor, build.timestamp from version ([1f56d6f](https://github.com/maxuser0/pyjinn/commit/1f56d6f232c034b4ce606d23a208dbfecf0c80ac))
- Support Java array on rhs of `in` operator ([3ec7633](https://github.com/maxuser0/pyjinn/commit/3ec76330fbd14dd12736b49a5725285e6c75ddd6))
- Support Outer.Nested class syntax ([29f988c](https://github.com/maxuser0/pyjinn/commit/29f988c8d318993a8829fded2635714615bfb4cf))
- Fix handling of int/long hex constants ([94c28f3](https://github.com/maxuser0/pyjinn/commit/94c28f36e9ab4d92ef7bda211a383c7dad8844c6))
- Refactor TypeChecker methods for easier reuse ([8f42a73](https://github.com/maxuser0/pyjinn/commit/8f42a73a78047016ef99465d575fa0548c7e57dc))
- Promote functional params for calls of Java ctors ([0383c7a](https://github.com/maxuser0/pyjinn/commit/0383c7a4d3107a9c5da026b5360daaf4561b05be))
- Support multi-threaded scripts, fix stack traces ([08955c4](https://github.com/maxuser0/pyjinn/commit/08955c4aea9cf9514775ec310468e4de40ff03c6))
- Update Pyjinn version to 0.5 ([4198082](https://github.com/maxuser0/pyjinn/commit/41980829965da80ecc6a25771c920ec6ca8aaf2c))
- Simplify implementation of slice expression parser ([456a4b1](https://github.com/maxuser0/pyjinn/commit/456a4b198a39050fdbefe2cce9b2fe125b813248))
- Fix slice parser to respect blank slice values ([817e8e2](https://github.com/maxuser0/pyjinn/commit/817e8e226c20d623c76324a07bc49e2a8c22664e))
- Fix handling of int/long hex constants ([94c28f3](https://github.com/maxuser0/pyjinn/commit/94c28f36e9ab4d92ef7bda211a383c7dad8844c6))
- Refactor TypeChecker methods for easier reuse ([8f42a73](https://github.com/maxuser0/pyjinn/commit/8f42a73a78047016ef99465d575fa0548c7e57dc))
- Promote functional params for calls of Java ctors ([0383c7a](https://github.com/maxuser0/pyjinn/commit/0383c7a4d3107a9c5da026b5360daaf4561b05be))
- Support multi-threaded scripts, fix stack traces ([08955c4](https://github.com/maxuser0/pyjinn/commit/08955c4aea9cf9514775ec310468e4de40ff03c6))
- Update Pyjinn version to 0.5 ([4198082](https://github.com/maxuser0/pyjinn/commit/41980829965da80ecc6a25771c920ec6ca8aaf2c))
- Simplify implementation of slice expression parser ([456a4b1](https://github.com/maxuser0/pyjinn/commit/456a4b198a39050fdbefe2cce9b2fe125b813248))
- Fix slice parser to respect blank slice values ([817e8e2](https://github.com/maxuser0/pyjinn/commit/817e8e226c20d623c76324a07bc49e2a8c22664e))
- Implement 'continue' statement inside loops ([0a91f61](https://github.com/maxuser0/pyjinn/commit/0a91f61a6677a6da9e82b46c19a2c7823aad0fe7))


## Minescript 5.0a4

- Built-in `eval` script is now implemented using Pyjinn (`eval.pyj`); Python eval command is now
  available as `pyeval`
- Introduce `set_interval()` and `set_timeout()` which behave similiarly to `setInterval()` and
  `setTimeout()` in JavaScript:
  - `set_interval(callback: Callable[..., None], timer_millis: int, *args) -> int`
  - `set_timeout(callback: Callable[..., None], timer_millis: int, *args) -> int`
- Introduce `remove_event_listener()` which cancels listeners using the int ID returned from
  `add_event_listener()`, `set_interval()`, and `set_timeout()`:
  - `add_event_listener(event_type: str, callback: Callable[..., None], **args) -> int`
  - `remove_event_listener(listener_id: int) -> bool`
- Basic support for `sys` module in Pyjinn scripts and stderr output:
  - `sys.argv, sys.exit(status=None), sys.version, sys.stdout, sys.stderr`
  - `print(..., file=sys.stderr)`
- Support for output redirection of Pyjinn scripts:
  - `\eval 'print("Send this message to other players via chat.")' > chat`
- Scripts can explicitly import the Pyjinn version of the Minescript standard library
  - for simple IDE integration (e.g. VSCode) use the module name relative to the `minescript` dir:
    - `import system.pyj.minescript`
    - `import system.pyj.minescript as m`
    - `from system.pyj.minescript import *`
  - for simpler imports and consistency with existing Python scripts you can use the short module name:
    - `import minescript`
    - `import minescript as m`
    - `from minescript import *`
- If there are no imports of `minescript` or `system.pyj.minescript` in the main script, it is
  imported implicitly as:
  - `from system.pyj.minescript import *`

## Minescript 5.0a3

Support for event listeners in Pyjinn scripts for these events:

- tick, render, key, mouse, chat, outgoing_chat_intercept, add_entity, block_update, explosion, take_item, damage, chunk

e.g.

```
frames = 0
def on_render(event):
  global frames
  frames += 1
  if frames % 1000 == 0:
    print(f"Rendered {frames} frames.")

add_event_listener("render", on_render)
```

Support for Minescript functions in Pyjinn scripts using the same API and syntax as Python scripts:

- execute, echo, echo_json, chat, log, screenshot, job_info, player_name, player_position, player_hand_items, player_inventory, player_inventory_select_slot, press_key_bind, player_press_forward, player_press_backward, player_press_left, player_press_right, player_press_jump, player_press_sprint, player_press_sneak, player_press_pick_item, player_press_use, player_press_attack, player_press_swap_hands, player_press_drop, player_orientation, player_set_orientation, player_get_targeted_block, player_get_targeted_entity, player_health, player, players, entities, version_info, world_info, getblock, getblocklist, screen_name, show_chat_screen, append_chat_history, chat_input, set_chat_input, container_get_items, player_look_at

## Minescript 5.0a2

This is the first step in calling Minescript script functions from
Pyjinn scripts, for example:

```
# pyjinn_test.pyj

Minescript = JavaClass("net.minescript.common.Minescript")

def call(func_name, args):
  return Minescript.call(func_name, args.getJavaList())

print(call("player_get_targeted_block", [20]))
print(call("world_info", []))
print("minescript version:", call("version_info", []).get("minescript"))
call("echo", ["hello?"])
call("echo_json", ['{"text": "hello", "color": "green"}'])
call("execute", ["time set day"])
```

## Minescript 5.0a1

**WARNING:** This is a pre-release version with features that are incomplete and APIs are subject to change and compatibility with the final release is not guaranteed.

Support for integrated [Pyjinn](https://github.com/maxuser0/pyjinn) interpreter. Files placed in the `minescript` directory ending in `.pyj` and written with Python syntax are interpreted without the need for an external Python installation. Minescript API functions are not yet supported from Pyjinn scripts. Java code can be run from scripts similar to Python scripts with Minescript 4.0 using [`lib_java.py`](https://minescript.net/sdm_downloads/lib_java-v2/).


## v4.1

- fixes to type hints in function signatures in `minescript.py` for improved integration with IDEs ([b2e8490](https://github.com/maxuser0/minescript/commit/b2e84901ccc2971bb0486fbe56df4f748499c848))

## v4.0

### Major changes

- Support running script functions during tick loop (20x per second), render loop (30-100+ fps), or script loop (~5000 times per second) ([923f9bb](https://github.com/maxuser0/minescript/commit/923f9bb5851ddf16273a3d9b0d0c8c23b75c2a68), [bfbd192](https://github.com/maxuser0/minescript/commit/bfbd192e782a8d0f01866fae6fb644f2cb0cb935), [fb936b8](https://github.com/maxuser0/minescript/commit/fb936b8f2000fbfde53e713a6b0ed56cb61a53f5))
- Java reflection via Python script functions, including ability to call script functions from Java
  ([ebdba54](https://github.com/maxuser0/minescript/commit/ebdba547152f4211939335f051ca7a55466b3374),
  [8008110](https://github.com/maxuser0/minescript/commit/80081103c03c3b539910370c007c0badb41fec6a),
  [39160c3](https://github.com/maxuser0/minescript/commit/39160c347659bc0667698a01f27effdf1e29027b),
  [8d97911](https://github.com/maxuser0/minescript/commit/8d979112e5e6109e0ba500a62f45852b7e250962))
- Introduce Tasks for efficient, synchronous batching of script function calls, including Java
  reflection
  ([d2bd144](https://github.com/maxuser0/minescript/commit/d2bd144ce056459708d75c2e71c28b535e48f308),
[1377acb](https://github.com/maxuser0/minescript/commit/1377acb6794d8ecdf9090984aa1618b37bad59b3),
[19eb79c](https://github.com/maxuser0/minescript/commit/19eb79c5bcf9ec1b416662a38608e82d29270e19),
[0762eaa](https://github.com/maxuser0/minescript/commit/0762eaac0a28b526dfc9502f143c51d0a2e81b59),
[12e7306](https://github.com/maxuser0/minescript/commit/12e7306682661e60e7bbb61557c3d7ab09788b65),
[f8ed176](https://github.com/maxuser0/minescript/commit/f8ed176e785258ac260dfc9bd0b749cc9c6d7917),
[023fd05](https://github.com/maxuser0/minescript/commit/023fd05b1aee172fea8fc0cb5b09cc954ff6e83e),
[1efbd90](https://github.com/maxuser0/minescript/commit/1efbd90105c5b4d5b75682bb7a15cd657e05b101),
[07388d9](https://github.com/maxuser0/minescript/commit/07388d971c053abce5af778ba1f437eb406f08e1))
- Support Python scripts in arbitrary folders using `command_path` in `config.txt` ([791c295](https://github.com/maxuser0/minescript/commit/791c2951dca52257e390e0f2b9cc6b28d76fedc9), [99c8619](https://github.com/maxuser0/minescript/commit/99c8619393830b99506c3c07bb33b5ddbd595dd0), [23ee273](https://github.com/maxuser0/minescript/commit/23ee273e8ac3a8740508ce7477ec09cfab03f2cb))
- Support alternative script languages for commands ([890d2a9](https://github.com/maxuser0/minescript/commit/890d2a9673b904f07574430f972c6403fbd9d363))
- Support for NeoForge, starting with Minecraft 1.20.2 ([7dd592f](https://github.com/maxuser0/minescript/commit/7dd592febcf3c851a7ac2724a06f0de040c1373b))
- Schedule groups of script functions to run every render or tick cycle with dynamically updated values ([19eb79c](https://github.com/maxuser0/minescript/commit/19eb79c5bcf9ec1b416662a38608e82d29270e19), [1377acb](https://github.com/maxuser0/minescript/commit/1377acb6794d8ecdf9090984aa1618b37bad59b3), [d2bd144](https://github.com/maxuser0/minescript/commit/d2bd144ce056459708d75c2e71c28b535e48f308), [0762eaa](https://github.com/maxuser0/minescript/commit/0762eaac0a28b526dfc9502f143c51d0a2e81b59))
- Support for scripting several game and input events with a unified `EventQueue` API ([d1cb4c9](https://github.com/maxuser0/minescript/commit/d1cb4c95ba380d9e10e9baed511a6bbd64076daf), [6628525](https://github.com/maxuser0/minescript/commit/662852592b3c506043aa2d45696a7660deeb7aa1), [993c9b6](https://github.com/maxuser0/minescript/commit/993c9b65eaf58c726dc50cc0e6c0d91be6b93403), [b96159e](https://github.com/maxuser0/minescript/commit/b96159e594768ec736d284ac1400e73a4646cea4), [4af1a32](https://github.com/maxuser0/minescript/commit/4af1a32c23d702591a385e6ade23ba5918fe0590))
- Async script functions that are more powerful, easier to use, and cancellable ([882bc7e](https://github.com/maxuser0/minescript/commit/882bc7e6cea6adc37b7366b0f72e64f5c9260873))
- Unified Minescript and Minecraft chat histories when using up/down arrows ([46a0cde](https://github.com/maxuser0/minescript/commit/46a0cdec9886ee601bc0c4dfeada48f33eb6e80a))
- New and updated script functions, including:
  - `press_key_bind()` ([174dac8](https://github.com/maxuser0/minescript/commit/174dac8d1d683fda96c4d74bf9e6d9ae804c7811))
  - `show_chat_screen()` ([adf8dac](https://github.com/maxuser0/minescript/commit/adf8dacaaec8810cca865c0d74ebe2fdaede88a2))
  - entity selectors for `entities()` and `players()` ([6d6d627](https://github.com/maxuser0/minescript/commit/6d6d627a3b45baeb3d98ea221d00b880c5b309d7))
  - `job_info()` for detecting other running scripts ([156dc33](https://github.com/maxuser0/minescript/commit/156dc331cb85af0e1254b3b9124baa0c960ce44c))
  - `version_info()` for detecting versions of Minecraft, Minescript, and OS ([d448c4b](https://github.com/maxuser0/minescript/commit/d448c4babfd4eb6fa229592140c349eda5b780a2))
  - multiple args to `echo()`, `chat()`, and `log()`, like builtin `print()` function ([114c559](https://github.com/maxuser0/minescript/commit/114c55920044683ad0eef2ecfb1b5afc6ae4f4b4))
  - `append_chat_history()`, `chat_input()`, and `set_chat_input()` for managing chat input and history ([46a0cde](https://github.com/maxuser0/minescript/commit/46a0cdec9886ee601bc0c4dfeada48f33eb6e80a))
  - `player_get_targeted_entity()` ([93dd5e8](https://github.com/maxuser0/minescript/commit/93dd5e8654027d7dc03b9078dd12f559150b1f55))
- Return complex data types from script functions as dataclasses (e.g. `EntityData`) rather than dicts, for easier and safer scripting ([7caaf3b](https://github.com/maxuser0/minescript/commit/7caaf3b988b886457ae644cd2109fef49acb5112))
- Support tab completion of `config` and `help` params ([b4bfa0e](https://github.com/maxuser0/minescript/commit/b4bfa0e426f1e1b09e8c40fe07f33ee48a92fa7b), [f485973](https://github.com/maxuser0/minescript/commit/f48597317220ad3ad5c574843a0cf7d43f199c78))
- Safer script output: `print()` no longer executes commands or sends public messages to chat by default ([6101484](https://github.com/maxuser0/minescript/commit/6101484fa57c2a0d8f34d6eeef94dd3b8ab8ceda))

### Detailed changes

- Update README for v4.0 and inclusion of neoforge ([f4e42f4](https://github.com/maxuser0/minescript/commit/f4e42f40fb5baf240d0a186870a2846b98521f4a))
- Update version numbers in gradle configs ([91941d2](https://github.com/maxuser0/minescript/commit/91941d22b23b094e4fe09405cde657a561825539))
- Update main branch to mc1.21.1 ([5b4d690](https://github.com/maxuser0/minescript/commit/5b4d69008bdd043b55381982e34806bc1846cd43))
- Fix bug in JobControl::log when no message args ([4c6b4f0](https://github.com/maxuser0/minescript/commit/4c6b4f0566a14b95cde66a09effa1a85ae4b4d55))
- Fix flaky async_function_test in minescript_test ([79c6559](https://github.com/maxuser0/minescript/commit/79c65598dd769a155058b82df73fd11605cc747c))
- Bump Minescript version from 4.0-beta2 to 4.0 ([7713ed4](https://github.com/maxuser0/minescript/commit/7713ed4526bf143bec92b13e188de06913ecba8c))
- Prevent async functions from running as tasks ([07388d9](https://github.com/maxuser0/minescript/commit/07388d971c053abce5af778ba1f437eb406f08e1))
- Rename ScriptFunctionArgList to ScriptFunctionCall ([0c45363](https://github.com/maxuser0/minescript/commit/0c453632e925483b0e200d35b5a9d6a0c0861ac3))
- Add java_call_script_function ([8d97911](https://github.com/maxuser0/minescript/commit/8d979112e5e6109e0ba500a62f45852b7e250962))
- Fix typo in documentation of async timeout ([4f49418](https://github.com/maxuser0/minescript/commit/4f494182b08c5f37daa9fff90639cd61ea5fc5c7))
- Update docs for tasks and async script functions ([1efbd90](https://github.com/maxuser0/minescript/commit/1efbd90105c5b4d5b75682bb7a15cd657e05b101))
- Update minescript_test.py to fix flakiness ([7850f61](https://github.com/maxuser0/minescript/commit/7850f61eb1356a52af94efb49013d0c3b921e18a))
- Simplify task tests with `append` helper function ([023fd05](https://github.com/maxuser0/minescript/commit/023fd05b1aee172fea8fc0cb5b09cc954ff6e83e))
- Remove obsolete split literal in "Level Loading" ([ee23600](https://github.com/maxuser0/minescript/commit/ee236007de4ae7ebd447f0c8ed7e38612488d31b))
- Update docs to include Tasks and drop _as_task arg ([f8ed176](https://github.com/maxuser0/minescript/commit/f8ed176e785258ac260dfc9bd0b749cc9c6d7917))
- Implement Task.contains for an item in container ([9e0f9a3](https://github.com/maxuser0/minescript/commit/9e0f9a3665a120a9b600ec7ed3453478416ab7c8))
- Add comparison operations to Java Numbers class ([13ca7d2](https://github.com/maxuser0/minescript/commit/13ca7d244f802d6a6b9c6e08b4ee1f11d6c343b3))
- Updates to script tasks, including Task.skip_if ([12e7306](https://github.com/maxuser0/minescript/commit/12e7306682661e60e7bbb61557c3d7ab09788b65))
- Change Fabric onRender event from LAST to END ([7c78636](https://github.com/maxuser0/minescript/commit/7c78636e55e444d1c7fef3a83e24501ef5d25ec5))
- Allow mod-loader builds to be disabled ([6f51835](https://github.com/maxuser0/minescript/commit/6f51835b2b67663aa9f70d4f7241842b83f7e2b9))
- Add more success messages in minescript_test ([658c2a4](https://github.com/maxuser0/minescript/commit/658c2a445928dbf1f7792bf39b15ffa327bc5c79))
- Update register_outgoing_chat_interceptor() pydoc ([2d82abe](https://github.com/maxuser0/minescript/commit/2d82abe1050e1ec4fa53a6a1f7e3e1174a15fa43))
- Update README.md with minescript module docs ([ad9eb5b](https://github.com/maxuser0/minescript/commit/ad9eb5bc6ce6c14e95155c72a6f31b7d82bd8dd8))
- Fix Python Java API docs, add java_null, add test ([fb320fc](https://github.com/maxuser0/minescript/commit/fb320fc74c88f42459ce9acff8c304a3ad1cd9f4))
- Add docstrings to EventQueue register methods ([0b2d1ff](https://github.com/maxuser0/minescript/commit/0b2d1ff4d5ab3d559b2b0cca75df6743047c34da))
- Update docs for v4.0 config ([56acb38](https://github.com/maxuser0/minescript/commit/56acb38c0bb8924e9ccc942dd92bcbf5254d1455))
- Fix bug in ScriptConfig for precondition check ([b0e6d07](https://github.com/maxuser0/minescript/commit/b0e6d0772dda6c1392b141423ee66a4799cb9e84))
- Remove legacy "minescript_"-prefixed config vars ([dbd8e98](https://github.com/maxuser0/minescript/commit/dbd8e989ecaa60f17b3818c7fd626315e209739e))
- Add "Since: 4.0" to java_release pydoc ([5e6652a](https://github.com/maxuser0/minescript/commit/5e6652adb658ac2798b712e5d8276d6dc9510156))
- Update doc strings for Python Java API functions ([7a9eabc](https://github.com/maxuser0/minescript/commit/7a9eabc480e013bcc1ee60eff15347a0340c76df))
- Add report_job_success_threshold_millis config var ([12569fe](https://github.com/maxuser0/minescript/commit/12569fef4285166fbb16f98de5a02cbfd95e149e))
- Add minecraft_class_name to version_info() data ([4fb9542](https://github.com/maxuser0/minescript/commit/4fb9542ebe62df45d8d01b5b93ebb765f3775ca4))
- Fix Forge mod for 1.21 running in non-dev mode ([a9a7abe](https://github.com/maxuser0/minescript/commit/a9a7abeed41dd08cd89037f0d52c27d762c4b9d0))
- Add support for Forge on Minescript 4.0, MC 1.21 ([d19ceb1](https://github.com/maxuser0/minescript/commit/d19ceb1ccc7aa1c5ab9c854177186d59a04c7073))
- Document key_mapping_name values to press_key_bind ([c358650](https://github.com/maxuser0/minescript/commit/c35865008a63ce958f1dc41d32a87742182e4dfb))
- Update to latest version of MultiLoader-Template ([2262b0f](https://github.com/maxuser0/minescript/commit/2262b0f9f4f2141a9bf4d56c6fc041b6381ca29f))
- Update to mc1.21 ([4276b39](https://github.com/maxuser0/minescript/commit/4276b398b0a48cf1ffb1ef4005fd77f1b58f36bf))
- Delete duplicate pack.mcmeta from neoforge build ([9e15517](https://github.com/maxuser0/minescript/commit/9e15517bdf7e6509515f23f2153850979f3a97f8))
- Update to mc1.20.6 and Java 21 ([9268867](https://github.com/maxuser0/minescript/commit/9268867494ae5ca3d3a1d6a90941ef606f4dda07))
- Revert "Update Minescript version to 4.0" ([5709e2c](https://github.com/maxuser0/minescript/commit/5709e2c47780f824fe49ec9af9bee498595effa4))
- Update docs for Minescript 4.0 ([b986258](https://github.com/maxuser0/minescript/commit/b9862588409d38a019008da177b2b3d91fa86d7d))
- Update Minescript version to 4.0 ([9b9fdb0](https://github.com/maxuser0/minescript/commit/9b9fdb0e7eb5cf69e033b7b5f68282e58e378263))
- Update changelog with changes in 4.0-beta2 ([97549f7](https://github.com/maxuser0/minescript/commit/97549f73c0bc32de45b26e5f764cf0f620836329))
- Bump mod version to 4.0-beta2 ([aaca0c0](https://github.com/maxuser0/minescript/commit/aaca0c0c2f03ca1b5004895026d69fd1119a4932))
- Support raw triple quotes in pydoc_to_markdown.py ([90888fc](https://github.com/maxuser0/minescript/commit/90888fc08540adc0dbbf5f7c0d3ce1e37461af0d))
- Ignore ID 0 in `java_release(...)` ([d9f0fd8](https://github.com/maxuser0/minescript/commit/d9f0fd8abff1a14d8a93f443a9a154cbc5dd8454))
- Return zeros for empty BlockPack.block_bounds() ([2152b7c](https://github.com/maxuser0/minescript/commit/2152b7ceed2a7f1d283850bfac4724f6a5656619))
- Update minescript_test with expect_lt and sleep ([8ddc72b](https://github.com/maxuser0/minescript/commit/8ddc72b5afc3bb9e895a2b1c6f5f54904db03548))
- Robustness improvements for Java reflection APIs ([f8373a7](https://github.com/maxuser0/minescript/commit/f8373a765a374f95fb6d9c755cb4be1d02eb8917))
- Improve error message from bad Java method call ([08a675c](https://github.com/maxuser0/minescript/commit/08a675c21eda1479253e94c5530f1e63c4d60ae8))
- Improvements to Java reflection API ([f36178f](https://github.com/maxuser0/minescript/commit/f36178fc5d059fcd83414c4223e6c6355d164fbb))
- Return bool from cancellation functions ([b2cdf35](https://github.com/maxuser0/minescript/commit/b2cdf35285f8e80a03a62a037ba779c7e59fe170))
- Simplify signature of `java_new_instance(...)` ([aae9662](https://github.com/maxuser0/minescript/commit/aae9662b4c87b76cc07da5ab54061ea97f1457c9))
- Return dataclass from  player_get_targeted_block ([e128d32](https://github.com/maxuser0/minescript/commit/e128d32d70fa2eeb6fa4861f7e5906e84b8e72ab))
- Replace deprecated Float ctor with Float::valueOf ([93a3436](https://github.com/maxuser0/minescript/commit/93a3436fdb354a79fcb8c829bfd87fcb266a6d14))
- Fix NPE in ExceptionInfo::fromException ([eb32887](https://github.com/maxuser0/minescript/commit/eb32887e10b2c68094b81ef0ed5542bbb84ab94e))
- Add Numbers class for numeric operations ([4464e0f](https://github.com/maxuser0/minescript/commit/4464e0f019bb1946368f9442ed302e92b44eb513))
- Remove unused import of asyncio from minescript.py ([ba821db](https://github.com/maxuser0/minescript/commit/ba821dbfa85bd935eeec485a6f7e6d79a048030e))
- Add java_assign script function for dynamic tasks ([0762eaa](https://github.com/maxuser0/minescript/commit/0762eaac0a28b526dfc9502f143c51d0a2e81b59))
- Update changelog for 4.0-beta ([2ada50a](https://github.com/maxuser0/minescript/commit/2ada50a0ddc0c4ee32e8145a610db400c3ffda5b))
- Update mod metadata description ([c2b2cbf](https://github.com/maxuser0/minescript/commit/c2b2cbfe37964c8582b03aa14731b9dcc6cf37d7))
- Break dep cycle between Minescript and Job classes ([7329962](https://github.com/maxuser0/minescript/commit/73299629e235cffe2ebbc28ec027c31b76b0f361))
- Change version to 4.0-beta ([15dcb74](https://github.com/maxuser0/minescript/commit/15dcb741a8a11160f41adfe9cc5bbbbfa4db7279))
- Remove script function `container_click_slot(int)` ([2224f58](https://github.com/maxuser0/minescript/commit/2224f58ebb6d26a413f1395aa532d81a57e1479f))
- Remove `player_set_position(...)` script function ([bfd25ee](https://github.com/maxuser0/minescript/commit/bfd25ee80cbfcfee3bca831579cbd73660b11efd))
- Support Java float with `java_float(...)` ([1177c0b](https://github.com/maxuser0/minescript/commit/1177c0ba7731b2cc1ae75e1e879198a5a7c7418a))
- Rename `path` config var to `command_path` ([791c295](https://github.com/maxuser0/minescript/commit/791c2951dca52257e390e0f2b9cc6b28d76fedc9))
- Fix bug where stderr wasn't echoed for quick fails ([895cf0b](https://github.com/maxuser0/minescript/commit/895cf0b4318b96211a77bd795713bd42724cd47a))
- Schedule tasks to run every render or tick cycle ([19eb79c](https://github.com/maxuser0/minescript/commit/19eb79c5bcf9ec1b416662a38608e82d29270e19))
- Add task operators for manipulating other tasks ([1377acb](https://github.com/maxuser0/minescript/commit/1377acb6794d8ecdf9090984aa1618b37bad59b3))
- Support dependencies among tasks via `run_tasks()` ([d2bd144](https://github.com/maxuser0/minescript/commit/d2bd144ce056459708d75c2e71c28b535e48f308))
- Switch to IntFunction overload of toArray methods ([c598e22](https://github.com/maxuser0/minescript/commit/c598e223f91fc2f165acd814a9acb2e610abc06a))
- Per-function executor selection from scripts ([923f9bb](https://github.com/maxuser0/minescript/commit/923f9bb5851ddf16273a3d9b0d0c8c23b75c2a68))
- Introduce JobInfo dataclass for `job_info()` ([0a6bc73](https://github.com/maxuser0/minescript/commit/0a6bc73a0ce67303bbdb7bd7bd0ec4e79ae5e580))
- Fix hang on script exit from cleanup script funcs ([1f7e966](https://github.com/maxuser0/minescript/commit/1f7e9668bbc0edc2fcafb0fe51eb224d8e1111a1))
- Improvements to stability and debugging ([0b7711b](https://github.com/maxuser0/minescript/commit/0b7711b5ee8d1af7fd3736b27447430d52daddb8))
- Unify script function APIs for async in Python ([882bc7e](https://github.com/maxuser0/minescript/commit/882bc7e6cea6adc37b7366b0f72e64f5c9260873))
- Config executors for servicing script functions ([bfbd192](https://github.com/maxuser0/minescript/commit/bfbd192e782a8d0f01866fae6fb644f2cb0cb935))
- Make key events consistent across mod loaders ([fb00a6c](https://github.com/maxuser0/minescript/commit/fb00a6c44b99d2f28f055c8cc86fe999d5834be6))
- Improve errors for Java-reflected method calls ([f44ce0b](https://github.com/maxuser0/minescript/commit/f44ce0bfc271f8b035140d6b034d9ae03b736db7))
- Unify Minescript and Minecraft chat histories ([46a0cde](https://github.com/maxuser0/minescript/commit/46a0cdec9886ee601bc0c4dfeada48f33eb6e80a))
- Add Java reflection functions to `minescript.py` ([ebdba54](https://github.com/maxuser0/minescript/commit/ebdba547152f4211939335f051ca7a55466b3374))
- Add id property to EntityData ([d8cb6c6](https://github.com/maxuser0/minescript/commit/d8cb6c6f665b087b5c9e7b2628710d226dec2b41))
- Fix `minescript_test.py` to expect ChatEvent ([2503847](https://github.com/maxuser0/minescript/commit/25038470dafc3346d3c3349ab2de5e60a379da82))
- Update Java reflection script functions ([8008110](https://github.com/maxuser0/minescript/commit/80081103c03c3b539910370c007c0badb41fec6a))
- Optimization for sub-millisecond script functions ([fb936b8](https://github.com/maxuser0/minescript/commit/fb936b8f2000fbfde53e713a6b0ed56cb61a53f5))
- Update config vars for managing command processing ([5204179](https://github.com/maxuser0/minescript/commit/520417986552a625919941dceb1118594fd3ee16))
- Add `press_key_bind()` script function ([174dac8](https://github.com/maxuser0/minescript/commit/174dac8d1d683fda96c4d74bf9e6d9ae804c7811))
- Reflection API for calling Java code from Python ([39160c3](https://github.com/maxuser0/minescript/commit/39160c347659bc0667698a01f27effdf1e29027b))
- Support listener for chunk load/unload events ([6628525](https://github.com/maxuser0/minescript/commit/662852592b3c506043aa2d45696a7660deeb7aa1))
- Rename `interpolated_position` to `lerp_position` ([f906838](https://github.com/maxuser0/minescript/commit/f90683823cf7be0d8c5e98514bf4c6c28a03d335))
- Support listeners for various types of game events ([993c9b6](https://github.com/maxuser0/minescript/commit/993c9b65eaf58c726dc50cc0e6c0d91be6b93403))
- Consolidate logic for event handler registration ([62915a2](https://github.com/maxuser0/minescript/commit/62915a201e1436776d3a21f21355b0131ff09cb9))
- Replace EventRegistrationHandler with EventQueue ([d1cb4c9](https://github.com/maxuser0/minescript/commit/d1cb4c95ba380d9e10e9baed511a6bbd64076daf))
- Export interpolated entity positions automatically ([08da0f6](https://github.com/maxuser0/minescript/commit/08da0f633658e5c177c49e73892d7ad96720f45f))
- Various performance optimizations ([1be6d40](https://github.com/maxuser0/minescript/commit/1be6d40e446deffd9df0b3f462ccf58b69316d43))
- Implement `version_info()` script function ([d448c4b](https://github.com/maxuser0/minescript/commit/d448c4babfd4eb6fa229592140c349eda5b780a2))
- Use level-specific build limits for blockpacks ([fc2ecdb](https://github.com/maxuser0/minescript/commit/fc2ecdb3002469a82e206cffafdc1f0925e17f1f))
- Add config vars for min/max blockpack y values ([3609cee](https://github.com/maxuser0/minescript/commit/3609cee4e27bbbf5ca1b957b9eccdd5736361e13))
- Remove "copies" from `IGNORE_DIRS_FOR_COMPLETIONS` ([4760bf0](https://github.com/maxuser0/minescript/commit/4760bf023c819ec9946882e52ca43990a7c3eff4))
- Add dataclasses for KeyEvent, MouseEvent, etc ([b96159e](https://github.com/maxuser0/minescript/commit/b96159e594768ec736d284ac1400e73a4646cea4))
- Return entities as Entity dataclass, not dicts ([7caaf3b](https://github.com/maxuser0/minescript/commit/7caaf3b988b886457ae644cd2109fef49acb5112))
- Fix file handle leak from Files.list() ([674954f](https://github.com/maxuser0/minescript/commit/674954f23f64727d7f505d008b3e18461d282b2c))
- Refactor into EntityExporter, support passengers ([29f8ea1](https://github.com/maxuser0/minescript/commit/29f8ea130b6db1ad97dd7090e5c1122a24c9dd8e))
- Add script function `job_info()` ([156dc33](https://github.com/maxuser0/minescript/commit/156dc331cb85af0e1254b3b9124baa0c960ce44c))
- Support multiple chat interceptors with filtering ([20007ff](https://github.com/maxuser0/minescript/commit/20007fff76752cefc2eea8dd6667c0251c693d21))
- Support command completion for param to `which` ([c1997fa](https://github.com/maxuser0/minescript/commit/c1997fa30a180b19c07c8ac5b2d6eb7bb7c12199))
- Multiple event handlers of same type in same job ([b700c71](https://github.com/maxuser0/minescript/commit/b700c718b6f73c27d39c495ab5c3ce5bbd02931e))
- Improve usability of echo() script function ([44ab9a5](https://github.com/maxuser0/minescript/commit/44ab9a57e1e262681d39987f91da7b10f61096a1))
- Improve handling of JsonSyntaxException for args ([d5f8c83](https://github.com/maxuser0/minescript/commit/d5f8c830e8140ca8eb5fe576a01da50b9b50930d))
- Implement `player_get_targeted_entity()` ([93dd5e8](https://github.com/maxuser0/minescript/commit/93dd5e8654027d7dc03b9078dd12f559150b1f55))
- Support configuration of a secondary `enter` key ([5f4b0ae](https://github.com/maxuser0/minescript/commit/5f4b0ae1c29742c9bc4c7c0788f06e18f2156afe))
- Improve thread-safety of script function calls ([47ef3a3](https://github.com/maxuser0/minescript/commit/47ef3a37b4772ab591941fbdc010b8974c85247e))
- Make `\help` echo same output as `\help help` ([d637df3](https://github.com/maxuser0/minescript/commit/d637df3a5f4b938a8406abc435a0861174947628))
- Fix crash when typing \\ on Windows, \/ elsewhere ([94487f8](https://github.com/maxuser0/minescript/commit/94487f85e8c22fea7047daf9cf813d2b2d0d89ec))
- Implement help for built-in commands with `\help` ([42b9322](https://github.com/maxuser0/minescript/commit/42b93224007970af1ffb23582d714a0581a022d8))
- Fix and cleanup config handling ([4702b72](https://github.com/maxuser0/minescript/commit/4702b72fa93282b7d7e65a44b523f8ee7d9c1a4b))
- Support tab completion of config and help params ([b4bfa0e](https://github.com/maxuser0/minescript/commit/b4bfa0e426f1e1b09e8c40fe07f33ee48a92fa7b))
- Implement `config [name [value]]` command ([f485973](https://github.com/maxuser0/minescript/commit/f48597317220ad3ad5c574843a0cf7d43f199c78))
- Refactor several classes out of Minescript class ([f80c67d](https://github.com/maxuser0/minescript/commit/f80c67d08bf2ef0eb506a3399f6f1c93c140e03e))
- Move config code to Config class ([1677d74](https://github.com/maxuser0/minescript/commit/1677d7474077846e60bb5b40f366c07ac5808b0e))
- Move MinescriptCommandHistory to its own file ([b0773d4](https://github.com/maxuser0/minescript/commit/b0773d411b0709dfd2760dfc97f98efcb6d017ef))
- Add `show_chat_screen(show, prompt)` script function ([adf8dac](https://github.com/maxuser0/minescript/commit/adf8dacaaec8810cca865c0d74ebe2fdaede88a2))
- Move version.txt to minescript/system dir ([031379a](https://github.com/maxuser0/minescript/commit/031379a4f7b4baa57c93245bd5c98921823d19e5))
- Escape double quotes in commands for Windows ([a8d0036](https://github.com/maxuser0/minescript/commit/a8d003670502e682eb15a8d31ea63366ff635711))
- Fix crash in onKeyboardKeyPressed when typing "\ " ([da75f45](https://github.com/maxuser0/minescript/commit/da75f450d7c783d07822d1ef1eef862258bb383d))
- Fix outdated numbers in computeRunLengths comment ([9d437e5](https://github.com/maxuser0/minescript/commit/9d437e53c4d90f84087fb8cb01a9f9d06c139a35))
- Safer script output: don't execute or send to chat ([6101484](https://github.com/maxuser0/minescript/commit/6101484fa57c2a0d8f34d6eeef94dd3b8ab8ceda))
- Use structured messages in Java for script output ([eb0c453](https://github.com/maxuser0/minescript/commit/eb0c453663758d26d36e8ca05f70070237805961))
- Support multiple args to echo(), chat(), and log() ([114c559](https://github.com/maxuser0/minescript/commit/114c55920044683ad0eef2ecfb1b5afc6ae4f4b4))
- Export `MINESCRIPT_COMMAND_PATH` env var to scripts ([c634692](https://github.com/maxuser0/minescript/commit/c634692f4665dba8f7fb4594f0fa66fc57d8076d))
- Show suggestions when tab completing empty prefix ([1401f42](https://github.com/maxuser0/minescript/commit/1401f42287c977c06094015eb35b0f7aae9dda20))
- Relocate built-in scripts to minescript/system ([23ee273](https://github.com/maxuser0/minescript/commit/23ee273e8ac3a8740508ce7477ec09cfab03f2cb))
- Support alternative script languages for commands ([890d2a9](https://github.com/maxuser0/minescript/commit/890d2a9673b904f07574430f972c6403fbd9d363))
- Support scripts in subdirs, configure script path ([99c8619](https://github.com/maxuser0/minescript/commit/99c8619393830b99506c3c07bb33b5ddbd595dd0))
- Fix Fabric mod to report `mod_id` as "minescript" ([f28d292](https://github.com/maxuser0/minescript/commit/f28d2926cf58c4d5223ad8b1f7068175a11f43e0))
- Add `player()` script function ([c215b6c](https://github.com/maxuser0/minescript/commit/c215b6cbd0fccda1140517dca46537392b5aea81))
- Fix naming of `min_distance`, `max_distance` in Python ([80a4dcc](https://github.com/maxuser0/minescript/commit/80a4dcc557a139b2c6b3b6c27366b6ffd5f5635a))
- Entity selector args for entities(), players() ([6d6d627](https://github.com/maxuser0/minescript/commit/6d6d627a3b45baeb3d98ea221d00b880c5b309d7))
- Support custom key binds for `player_press_* funcs` ([cb70c2c](https://github.com/maxuser0/minescript/commit/cb70c2c26d7e1cb8bfe8e031a2769edd37c6c182))
- Get ChatScreen.input using mixin, not reflection ([da02694](https://github.com/maxuser0/minescript/commit/da026945cb62cb1e69621b2dbf4e4943bcda71eb))
- Add x, y to pydoc of MouseEventListener.get(...) ([93a93d9](https://github.com/maxuser0/minescript/commit/93a93d9a83aa40f481a3d6321af78348a43f860c))
- Support MouseEventListener to handle mouse clicks ([4af1a32](https://github.com/maxuser0/minescript/commit/4af1a32c23d702591a385e6ade23ba5918fe0590))
- Refactor entitiesToJsonArray to entityToJsonObject ([802b063](https://github.com/maxuser0/minescript/commit/802b063cf30e20fe6b7f27e524c216a62d89e623))
- Support `with` expression for event listeners ([a8f917b](https://github.com/maxuser0/minescript/commit/a8f917b517d1fa5aed3564cc076a749582ef7534))
- Remove legacy undo and copy settings ([0423187](https://github.com/maxuser0/minescript/commit/04231872cc74765315d77dce316357a8edc8b0ad))
- Move checks of script function args to new class ([7316b85](https://github.com/maxuser0/minescript/commit/7316b85ce02f2c3e9189e637a599cd9012bc8e8b))
- Fix `player_set_position` impl to require 5 args ([8daa1cc](https://github.com/maxuser0/minescript/commit/8daa1cc8ddc6f3a2932ad701c7271d2c4704fe0d))
- Throw exceptions for bad script function calls ([7045f5f](https://github.com/maxuser0/minescript/commit/7045f5fd0d25c337bb70d6dcfbf1d2626274110c))
- Return Java exception info, stacktrace to Python ([ce9a1d7](https://github.com/maxuser0/minescript/commit/ce9a1d7f527fa4be1bbf462f7eee08d88ca9daa0))
- Replace custom JSON format output with Gson APIs ([d92deb0](https://github.com/maxuser0/minescript/commit/d92deb0d5f06671b4acc91080fa9188b13fd2ad3))
- Delete copy.py when upgrading to Minescript 4.0 ([f3f8c60](https://github.com/maxuser0/minescript/commit/f3f8c606f4df515ed699eaf8016cd0c827f8db5b))
- Support timeout, cleanup of `await_loaded_region()` ([59d34f4](https://github.com/maxuser0/minescript/commit/59d34f4aab9b1bdbcfa38bf7ff9d6b32ce6fd0d4))
- Convert async script functions to async/await ([b4558bf](https://github.com/maxuser0/minescript/commit/b4558bf2e53190d37af89896d2812f3927fe32d9))
- Move ChatComponentMixin to common project ([9916184](https://github.com/maxuser0/minescript/commit/9916184d9bc64a61be5cf8c5d2210322ea1ece1c))
- Upgrade version of new script functions to v4.0 ([c4b1ff8](https://github.com/maxuser0/minescript/commit/c4b1ff844a22481798ac02d74a9d89d343507c2a))
- Fix ChatComponent mixin for NeoForge mod ([de085b4](https://github.com/maxuser0/minescript/commit/de085b456f12aedacf1e0086743287ccf0583110))
- Initial version of Minescript mod for NeoForge ([7dd592f](https://github.com/maxuser0/minescript/commit/7dd592febcf3c851a7ac2724a06f0de040c1373b))
- Upgrade Minescript version from v3.2 to v4.0 ([ada94db](https://github.com/maxuser0/minescript/commit/ada94db9c1b07aff2830edccb8e1bfb185cc89e6))
- Rename copy.py to `copy_blocks.py` ([692cd54](https://github.com/maxuser0/minescript/commit/692cd54e1115a181705401d73d5241cac44ebc07))
- Limit version number updates to common directory ([af0d38c](https://github.com/maxuser0/minescript/commit/af0d38c9058955c0c9b5442eb3e641615254f6a2))
- Move build instructions to top-level README file ([889a895](https://github.com/maxuser0/minescript/commit/889a895eb78b1a526cf75940dcf39006d613e31b))
- Refactor code in Forge mod to isolate client code ([9ec35bd](https://github.com/maxuser0/minescript/commit/9ec35bd3e370e12d0445400912efbcc54c94497e))
- Refactor code in Fabric mod to isolate client code ([a9e5486](https://github.com/maxuser0/minescript/commit/a9e54866d9fc7172811c8b6cbdee0b39240928a2))
- Call Minescript.init() on Forge only client-side ([165fe3d](https://github.com/maxuser0/minescript/commit/165fe3df2d8c0165c5e9aac72d7245b3e9cfb781))
- Update copyright year to include 2024 ([d6d9e64](https://github.com/maxuser0/minescript/commit/d6d9e64e0d328c4cc96db083d4f2248c130a39b9))
- Refactor common sources into common project dir ([a2ae981](https://github.com/maxuser0/minescript/commit/a2ae9812cf854da05fbc2f3f31e8db7b6fa5d64d))
- Use same source mappings for Forge and Fabric mods ([f8f12ba](https://github.com/maxuser0/minescript/commit/f8f12ba6339a31fbfdb48223c217498d4811e779))
- Port new script functions from Fabric to Forge ([31af781](https://github.com/maxuser0/minescript/commit/31af78134981fbb104e0a99f424afeb6c0f39efc))
- Added Container Functions (#8) ([8d4de9e](https://github.com/maxuser0/minescript/commit/8d4de9ef9e5e839ccb70e2d4a8de49c789a9c11a))

## v3.2

### Major changes

- Support for Minecraft versions up to 1.20.4
- Fix Forge bug where Minescript loop could run on the wrong thread and crash, and improve performance (especially with Fabric) by setting the default for `minescript_ticks_per_cycle` to run every tick rather than once every 3 ticks ([79613b1](https://github.com/maxuser0/minescript/commit/79613b14abf05691199139236f4b826265080f99))
- Add script function `screen_name()` for getting the name of the currently displayed GUI screen, if any ([e40d52a](https://github.com/maxuser0/minescript/commit/e40d52a184d315cfa98549381074de49a07dbb20))
- Introduce `ChatEventListener` Python class for more user-friendly handling of chat events ([83dd4aa](https://github.com/maxuser0/minescript/commit/83dd4aafb19a514d217132113c9ec102f1e92960))
- Support propagation of Java exceptions to Python ([2c8692d](https://github.com/maxuser0/minescript/commit/2c8692d3c43891a8e0cbc0d43169598ec077d0a1))
- Support for listening for keyboard events with `KeyEventListener` Python class ([b0163d3](https://github.com/maxuser0/minescript/commit/b0163d3f96afcefc42fc22b7bc4fb8b21d142f28))
- Fix `player_inventory_slot_to_hotbar()` "ghost items" in survival mode ([c63fd27](https://github.com/maxuser0/minescript/commit/c63fd27b776e778365fd0fdbc0488377b4111c5c), [49ee542](https://github.com/maxuser0/minescript/commit/49ee542fe866ac9d5c9cbe2d1d6717a6ff84d5ac))

### Detailed changes

- Update MC version to 1.20.2 ([b4593a4](https://github.com/maxuser0/minescript/commit/b4593a4456120f02aecef9b92fbcd871fc627d6e))
- Update Fabric, Forge to latest stable versions ([155ca0a](https://github.com/maxuser0/minescript/commit/155ca0a84ede036910b5435821a3e52fe59a29a6))
- Update docs for v3.2 ([ca04b0b](https://github.com/maxuser0/minescript/commit/ca04b0bfe07bc31ea7f6d80e583c55dc7915df44))
- Update Minescript version to 3.2 ([3f3d640](https://github.com/maxuser0/minescript/commit/3f3d640dbe0eabd838b0b97e4fa0fafd9959d939))
- Fix Forge bug: ran Minescript loop at wrong times ([79613b1](https://github.com/maxuser0/minescript/commit/79613b14abf05691199139236f4b826265080f99))
- Add script function: screen_name() ([e40d52a](https://github.com/maxuser0/minescript/commit/e40d52a184d315cfa98549381074de49a07dbb20))
- Introduce ChatEventListener Python class ([83dd4aa](https://github.com/maxuser0/minescript/commit/83dd4aafb19a514d217132113c9ec102f1e92960))
- Support propagation of Java exceptions to Python ([2c8692d](https://github.com/maxuser0/minescript/commit/2c8692d3c43891a8e0cbc0d43169598ec077d0a1))
- Keyboard events: onKeyboardEvent, KeyEventListener ([b0163d3](https://github.com/maxuser0/minescript/commit/b0163d3f96afcefc42fc22b7bc4fb8b21d142f28))
- Fix player_inventory_slot_to_hotbar() for forge ([c63fd27](https://github.com/maxuser0/minescript/commit/c63fd27b776e778365fd0fdbc0488377b4111c5c))
- Fix minescript.player_inventory_slot_to_hotbar() (#6) ([49ee542](https://github.com/maxuser0/minescript/commit/49ee542fe866ac9d5c9cbe2d1d6717a6ff84d5ac))
- Update forge, fabric from Minecraft 1.20 to 1.20.1 ([9c60981](https://github.com/maxuser0/minescript/commit/9c6098103c997e539ad7811b8348b093abe298ea))
- Update to MC version 1.20 ([aaada38](https://github.com/maxuser0/minescript/commit/aaada3839cd66d9369482e890f97b2f888c89558))
- Add stderr_chat_ignore_pattern variable to config ([bf8a861](https://github.com/maxuser0/minescript/commit/bf8a861465afb508a3f5753c079bc43984c6d868))
- Update gradle.properties to mc1.19.4 ([42915e1](https://github.com/maxuser0/minescript/commit/42915e14bcb32fafd5dd537d7b04c604004b44d0))
- Fix doc formatting for conversion to HTML ([c2d0f3c](https://github.com/maxuser0/minescript/commit/c2d0f3ca7c53d10e60a5271422a0c6e4b6f8a53a))

## v3.1

### Major changes

- New `autorun[world]=\command` configuration in `config.txt` to run commands
  automatically when joining a world.
  ([e135fa7](https://github.com/maxuser0/minescript/commit/e135fa733764c749ca6f8588a3271204a3c30099))
  - The form of these config lines is:
    `autorun[World Name]=\command_to_run with args`
  - The special name `*` indicates that the command should be run when entering
    all worlds:
    `autorun[*]=\command_to_run with args` (backslash before the command name is optional)
  - Lines ending with a single backslash are implicitly joined with the following
    line, for example:
    ```
    autorun[*]=\
      eval '"*** Welcome to %s ***" % world_properties()["name"]'; eval world_properties()
    ```
  - As in the previous example, multiple Minescript commands can now be
    specified on the same line, separated by semicolons, to run them in
    sequence (each command must finish before the next command begins). This
    applies to both `autorun` commands in `config.txt` and the in-game chat.
- Script jobs are now automatically killed when leaving a world.
- Fix bug with missing `fcid` (function call ID) on Windows ([9183285](https://github.com/maxuser0/minescript/commit/91832859731f100b562cf922e5ab3a5e1c476159))
- New built-in script command `eval`; see [`eval.py`](https://github.com/maxuser0/minescript/blob/main/fabric/src/main/resources/eval.py)
- New script function `world_properties()`; see [documentation](https://github.com/maxuser0/minescript/blob/22ba0feb5056fa2e4b606663f413716f316ea853/docs/README.md#world_properties)
- NBT output for `players(nbt=True)` and `entities(nbt=True)`; see
  [players](https://github.com/maxuser0/minescript/blob/22ba0feb5056fa2e4b606663f413716f316ea853/docs/README.md#players)
  and
  [entities](https://github.com/maxuser0/minescript/blob/22ba0feb5056fa2e4b606663f413716f316ea853/docs/README.md#entities)
- Significant performance optimization of `BlockPacker.setblock(...)` and `BlockPacker.fill(...)`.
- New script function `player_set_position(...)`; see [documentation](https://github.com/maxuser0/minescript/blob/22ba0feb5056fa2e4b606663f413716f316ea853/docs/README.md#player_set_position)
- Fix locale-specific formatting of floats in JSON ([525e2c1](https://github.com/maxuser0/minescript/commit/525e2c1ce234bf7e1630bf061db12ccc1f4a4695))

### Detailed changes

- Add Coolbou0427 to credits for testing on Windows ([957d5c2](https://github.com/maxuser0/minescript/commit/957d5c25159502560fd52ed7350157687173def3))
- Fix copy_paste_test in minescript_test on Windows ([0261a14](https://github.com/maxuser0/minescript/commit/0261a14c4b3805e14db60c9183b56d62a9d19e8d))
- Fix bug with missing func_call_id on Windows ([9183285](https://github.com/maxuser0/minescript/commit/91832859731f100b562cf922e5ab3a5e1c476159))
- Update changelog with new built-in script eval.py ([cfc3a8e](https://github.com/maxuser0/minescript/commit/cfc3a8e967d38a590f1c209ff5367203e0ccfa3a))
- Rename "General commands" to "Built-in commands" ([1e3f36f](https://github.com/maxuser0/minescript/commit/1e3f36fddfd405e550fc41d23c2358a3a4b086b2))
- Add warning to eval.py about being overwritten ([fb91cce](https://github.com/maxuser0/minescript/commit/fb91cce08b3c3b286aa34923e1cd3975d92dfced))
- Add eval.py to minescript jar as a built-in script ([5038381](https://github.com/maxuser0/minescript/commit/5038381340db4b28b1c1a562df0a783a431eb082))
- Add Configuration section to docs ([9484164](https://github.com/maxuser0/minescript/commit/94841641f55ee6cb2d215f2981fedde0a75a4604))
- Update CHANGELOG.md for v3.1 ([7314e5f](https://github.com/maxuser0/minescript/commit/7314e5f3362f4f947a61d14287304053e7cae7f1))
- Fix typos in minescript.py, update docs/README.md ([22ba0fe](https://github.com/maxuser0/minescript/commit/22ba0feb5056fa2e4b606663f413716f316ea853))
- Support multi-line config vars in config.txt ([5f2aa53](https://github.com/maxuser0/minescript/commit/5f2aa53e4b0a663c5cd26e3daafd6625c8e7c51b))
- Support compound commands delimited by semicolons ([27f3480](https://github.com/maxuser0/minescript/commit/27f3480ad4cf2d3034e191be56bb91e12370da33))
- Fix timing bug with autorun when entering world ([3953d37](https://github.com/maxuser0/minescript/commit/3953d37d688c06741e07272557901f19e4c519df))
- Rename Token.Type.ARG to Token.Type.STRING ([697bf94](https://github.com/maxuser0/minescript/commit/697bf94718b1b15aea1b82c14405370a983be9b9))
- Introduce tokenized command parsing for: && || ; ([513f852](https://github.com/maxuser0/minescript/commit/513f8522388598d0fadc6d7b805892db8def7798))
- Implement autorun to run commands when join world ([e135fa7](https://github.com/maxuser0/minescript/commit/e135fa733764c749ca6f8588a3271204a3c30099))
- Changes to job management and world exit handling ([a3a7ae7c](https://github.com/maxuser0/minescript/commit/a3a7ae7cfa1d29b5b640200366d7afb76e725c4c))
- Update docs for v3.1 ([3b6e77b](https://github.com/maxuser0/minescript/commit/3b6e77b1ac858cc5f59c78f16710aa0f10e70bd6))
- Add script function world_properties() ([f6dfb49](https://github.com/maxuser0/minescript/commit/f6dfb493cac11a18548fe6be6e8a65f61e081aee))
- Optional NBT output for players() and entities() ([5e9492c](https://github.com/maxuser0/minescript/commit/5e9492c50fb45f2912ede4ed0af33294ba59a670))
- Add "local" to local player in players/entities() ([cbdb692](https://github.com/maxuser0/minescript/commit/cbdb692ffe89793c0f1d3133a0c87785c35cd754))
- Update docs for v3.1 ([ea95388](https://github.com/maxuser0/minescript/commit/ea9538883c3ec2b692593a07ef39daed1ad2a464))
- Fork v3.0 docs to v3.1 ([61b4f3a](https://github.com/maxuser0/minescript/commit/61b4f3a7f0255cf81c13064820c5643f10036ad1))
- Add functionality for reporting health ([f46967b](https://github.com/maxuser0/minescript/commit/f46967bf3223b0ebf92d5f2fc56a7366162ae699))
- Fix formatting of NBT data as JSON in inventory ([3bddc19](https://github.com/maxuser0/minescript/commit/3bddc1976cc0821fa420f913edfaf37afd31dacd))
- Optimize command loop to bail if no more commands ([8ed8b38](https://github.com/maxuser0/minescript/commit/8ed8b3825929d2ec0520a2e9dcbbef8385950ae4))
- Add script function player_set_position ([d5e3063](https://github.com/maxuser0/minescript/commit/d5e3063c7adc6fa8216c08b76f231dda88141ba8))
- Add script function blockpacker_add_blocks(...) ([24f7f71](https://github.com/maxuser0/minescript/commit/24f7f71027631b02954da8101d31fe5b290709c2))
- Fix BlockPacker.fill so points can be in any order ([eebfde8](https://github.com/maxuser0/minescript/commit/eebfde8967706b9cd56d3ce9d13aa18d3d8fd5cf))
- Fix locale-specific formatting of floats in JSON ([525e2c1](https://github.com/maxuser0/minescript/commit/525e2c1ce234bf7e1630bf061db12ccc1f4a4695))
- Update copyright year for 2023 ([72e6a55](https://github.com/maxuser0/minescript/commit/72e6a551f453645e7a586c09b6c88401c654831b))
- Update Minescript version from v3.0 to v3.1 ([f142925](https://github.com/maxuser0/minescript/commit/f1429256fda87b36b09499529aab69514f95c9c7))

## v3.0

### Major changes

- Introducing blockpacks! Blockpacks are collections of blocks that can be serialized (saved to a file or sent across a network), rotated, flipped, and combined for complex builds made of reusable structures. See [documentation](https://github.com/maxuser0/minescript/blob/3d4ae355a20a660001c7c240fdd29c7bc58ae3be/fabric/src/main/resources/minescript.py#L700).
- `copy`-`paste` and `undo` performance improved by up to 20x from earlier versions, using new implementation based on blockpacks
- `copy` script now outputs zip files (zipped blockpacks) in `blockpacks` directory (previously txt files in `copies` directory)
- New script functions:
    - `player_inventory_slot_to_hotbar`: swaps an inventory item into the hotbar and selects that hotbar slot into the player's hand; see [documentation](https://github.com/maxuser0/minescript/blob/3d4ae355a20a660001c7c240fdd29c7bc58ae3be/fabric/src/main/resources/minescript.py#L159)
    - `player_inventory_select_slot`: selects the given slot within the player's hotbar; see [documentation](https://github.com/maxuser0/minescript/blob/3d4ae355a20a660001c7c240fdd29c7bc58ae3be/fabric/src/main/resources/minescript.py#L177)
    - `player_get_targeted_block`: gets info about the nearest block, if any, in the local player's crosshairs; see [documentation](https://github.com/maxuser0/minescript/blob/3d4ae355a20a660001c7c240fdd29c7bc58ae3be/fabric/src/main/resources/minescript.py#L324)
- Removed script functions deprecated in v2.1: `pyexec`, `exec` (subsumed by `execute`)

### Detailed changes

- Update CHANGELOG.md for v3.0 ([1be5136](https://github.com/maxuser0/minescript/commit/1be513661fe5f807cef5b3515fd67211e76a37be))
- Update copy.py, paste.py requirements to v3.0 ([d52cf35](https://github.com/maxuser0/minescript/commit/d52cf35f4a8d93a74a24fd39fe3871e3ea062040))
- Update minescript module section of docs/README.md ([2266eba](https://github.com/maxuser0/minescript/commit/2266eba23ea87f41885860c0fe1039cc2d28e597))
- Fix off-by-one error in copy script's info message ([c132699](https://github.com/maxuser0/minescript/commit/c1326994d56a7e6f56a1430d1d3cd8b69c4703e4))
- Fix copy_paste_test, and other test improvements ([1d6384c](https://github.com/maxuser0/minescript/commit/1d6384cb35f35a18205746c2eee1a2b832f935cb))
- Update tests for Minescript v3.0 ([0947d3e](https://github.com/maxuser0/minescript/commit/0947d3e30ff73196e0a95ff44e801ba14ad41a3b))
- Update forge and fabric mods to mc1.19.3 ([1c895fb](https://github.com/maxuser0/minescript/commit/1c895fb99c078f8aba01586cfbb43f13c3e65c8e))
- Update Minescript version from v2.2 to v3.0 ([c034b48](https://github.com/maxuser0/minescript/commit/c034b48733c8131322ff1eecdc7a792bf22a7c60))
- Add docstrings for all blockpack, blockpacker code ([e5aaf1a](https://github.com/maxuser0/minescript/commit/e5aaf1ab8efb7ef43496fdce67b46d93fad6d34a))
- Fix error handling of blockpack_read_world ([96f542f](https://github.com/maxuser0/minescript/commit/96f542f65a4f533343a2856f7c362bc65ff4a886))
- Remove pyexec and exec functions in prep for v3.0 ([430fe08](https://github.com/maxuser0/minescript/commit/430fe082207c50b6a19e48d9b140e3795147ad69))
- Clarify error messages when reading blockpacks ([2832338](https://github.com/maxuser0/minescript/commit/283233853274287ee5af41ad4517ef247516a2e1))
- Add player_get_targeted_block(...) script function ([6d44c0e](https://github.com/maxuser0/minescript/commit/6d44c0e090c9273727741091600fde58c0791f04))
- Add stock rotations and means of combining them ([8673227](https://github.com/maxuser0/minescript/commit/867322751e0e4082d03d1809c2ecc0ef129b3677))
- Use blockpacks dir for copies and default location ([b368fab](https://github.com/maxuser0/minescript/commit/b368fab45363d773cc45337e2abe7cd74fcd28fe))
- Improve handling of blockpack file IO errors ([0acbe35](https://github.com/maxuser0/minescript/commit/0acbe3557148aee6e0be1a9d34165f74f0d7a480))
- Update javadoc: mapDirectionToXZ, mapXZToDirection ([46059c5](https://github.com/maxuser0/minescript/commit/46059c50e38f3b1d0c03b79af65639073f869222))
- Support rotation of directional blocks in xz plane ([daf95f0](https://github.com/maxuser0/minescript/commit/daf95f01d79fb5cb543a993420533e68b4cb67ce))
- Support rotation everywhere offset is supported ([f614cbb](https://github.com/maxuser0/minescript/commit/f614cbb945dd983505371bbdf131a2afbdea929a))
- Merge BlockPack offset and rotation into transform ([e6924bc](https://github.com/maxuser0/minescript/commit/e6924bcbde2d03d02e1db538adb1e653985552d1))
- Support rotations in BlockPacker.add_blockpack() ([a8d6c28](https://github.com/maxuser0/minescript/commit/a8d6c284b1f055b1b07d390388e27ab6e1bf736c))
- Implement BlockPacker script API ([96b4bc7](https://github.com/maxuser0/minescript/commit/96b4bc79b75c7df78764498d7e45f48bc9af2fe0))
- Track script-generated BlockPacks per script job ([89e7013](https://github.com/maxuser0/minescript/commit/89e70134934aea25070de13fd3624e2a55b4801b))
- Support import/export of BlockPack data as base64 ([6470bc2](https://github.com/maxuser0/minescript/commit/6470bc28adc81642ac4e8f4f6650119094e88023))
- Create copies dir as needed from copy.py ([608c0d9](https://github.com/maxuser0/minescript/commit/608c0d982bba801c5aae7d1a67a927dd70a7a3a2))
- Re-implement copy & paste via Python BlockPack API ([2cf700e](https://github.com/maxuser0/minescript/commit/2cf700e1a20adddbf61102a633e3ad9766135b16))
- Sync changes from fabric to forge sources ([785aaab](https://github.com/maxuser0/minescript/commit/785aaab1cfee85384a7ff853c84b1d7244c95f7a))
- Fix crash bug when pressing backslash and space ([86a19b7](https://github.com/maxuser0/minescript/commit/86a19b70d7bb3a920e10b4c899113dc72ea0e0c9))
- Implement undo command using BlockPack ([b2bf889](https://github.com/maxuser0/minescript/commit/b2bf88943e5cd56e79bb5b97f3a7dcc63ce23c91))
- Merge branch 'main' into blockpack ([739db6a](https://github.com/maxuser0/minescript/commit/739db6ae8738a44b7304cd703149062be21bbb8e))
- Add comments (string->string map) to BlockPack ([cddc540](https://github.com/maxuser0/minescript/commit/cddc5402c65f08cd38d8c7ff045b875321506d5c))
- Make BlockPack.readStream(...) more robust ([495c8d4](https://github.com/maxuser0/minescript/commit/495c8d4a27c6fa0a4a525d4a9ffb153113eab70e))
- Compute tile and block bounds in BlockPack ctor ([38cf073](https://github.com/maxuser0/minescript/commit/38cf0730c591b1267cbef4ab2c7ac936b689b3b2))
- Change magic bytes, inner file extension to .blox ([916c4a2](https://github.com/maxuser0/minescript/commit/916c4a2aeae347cdd4b4400ce2a8d04cf26aed45))
- Change BlockPack format to use extensible chunks ([caf3a7a](https://github.com/maxuser0/minescript/commit/caf3a7a9167877b826fa348fd99d8abefb522084))
- Implement BlockPack writeFile and readFile ([676a07a](https://github.com/maxuser0/minescript/commit/676a07a28023d93db1f50df4a2af6af28f6b31a2))
- Update BlockPack tile key, simplify implementation ([b434a16](https://github.com/maxuser0/minescript/commit/b434a162255678026b13f189f54a81af5489828c))
- Implement BlockPack.getBlockCommands(...) ([313f6f3](https://github.com/maxuser0/minescript/commit/313f6f3f54812bc472168d39a67e5b875ce49011))
- Replace symbolMap.containsKey/put with putIfAbsent ([d083ef3](https://github.com/maxuser0/minescript/commit/d083ef3005405b06b5ac282ccf2d5e5e3d32961c))
- Use short[] for setblocks and fills in BlockPack ([b0a01a4](https://github.com/maxuser0/minescript/commit/b0a01a40b294015d04d9cb1e1d8755c6ad22bd2b))
- Optimize BlockPack setblocks and fills space used ([61b035c](https://github.com/maxuser0/minescript/commit/61b035cd1c797b519a4cbe8cf8a5cfafcec9ca9b))
- Add debugging info for measuring pack's byte size ([1593348](https://github.com/maxuser0/minescript/commit/1593348cc945ba5d26fc7d272af591fbef0342c4))
- Initial (incomplete) impl of BlockPack.BlockType ([442ebd6](https://github.com/maxuser0/minescript/commit/442ebd67f3cdce7e9159cf04fa63e56721ce5314))
- BlockPack.Tile.printBlockCommandsInAscendingYOrder ([1249cff](https://github.com/maxuser0/minescript/commit/1249cffeef4d1bafb145b69a4f72a4a96db1d138))
- Introduce 2-level symbol table for block types ([5179643](https://github.com/maxuser0/minescript/commit/51796432b7a62394ca64f0a05f5063d4ebbda50f))
- Reuse block type IDs for overwritten unique blocks ([bf319d1](https://github.com/maxuser0/minescript/commit/bf319d1857ffe0c4e60ee264a3e2786f5c659831))
- Fix block-type frequencies in BlockPacker.Tile ([f5f7125](https://github.com/maxuser0/minescript/commit/f5f71256991375f7c205b0df153538ee1cbcf89b))
- Allow BlockPacker to incrementally pack blocks ([260be62](https://github.com/maxuser0/minescript/commit/260be6239da58fe55976e3783652adbaaf497323))
- Fix bug in BlockPacker.Tile.computeBlockCommands ([c73ed16](https://github.com/maxuser0/minescript/commit/c73ed16870fa7248fa176d629ce799824f2a41ea))
- Fix formatting of javadoc in BlockPack\*.java ([67f6d2c](https://github.com/maxuser0/minescript/commit/67f6d2c3e04c2350f4562cefa407f305c3ca82ac))
- Add copyright and GPL3 license to BlockPack\*.java ([3c6d5f5](https://github.com/maxuser0/minescript/commit/3c6d5f54bc8ee712bafcb3ac3325f60284fc7640))
- Refactor Volume into BlockPacker and BlockPack ([2e38c6c](https://github.com/maxuser0/minescript/commit/2e38c6c25c18fac6056e0b6dcf941410a9a7924b))
- Initial impl of Volume (to be renamed BlockPack) ([d07782e](https://github.com/maxuser0/minescript/commit/d07782e7a0bccd2bdddaa4321c04555671894e5c))
- Allow paste command to parse fill commands ([89dd27c](https://github.com/maxuser0/minescript/commit/89dd27cf8513f101e64ed5a74e8b2645e2fe8f8e))
- Document v2.2 changes in player_inventory behavior ([1a64dc5](https://github.com/maxuser0/minescript/commit/1a64dc5afdefbaabc0c3225330f78fd878244396))
- Add script functions to manage inventory, hotbar ([15757ba](https://github.com/maxuser0/minescript/commit/15757ba3bd8bcadfe6de239a7d40d9cedd293c1b))
- Update Minescript version to 2.2 ([49a6fc2](https://github.com/maxuser0/minescript/commit/49a6fc2339bb0a8872a364cfb0eac14059a7c172))
- Rewrite "Previous version" only when forking docs ([1d1a153](https://github.com/maxuser0/minescript/commit/1d1a153bcc0b6413df590ef613b7da7440bda5d5))
- Change terminology from "branch" to "fork" ([2967b04](https://github.com/maxuser0/minescript/commit/2967b04a9863c0a0fb0b7d6fc16ea77d92a1d954))
- Merge fabric/forge test into single top-level test ([702d58f](https://github.com/maxuser0/minescript/commit/702d58fcc68ac8fa34eda5acf0e33aaf21231b10))

## v2.1
- Additional changelog entries for v2.1 ([f5b9dc8](https://github.com/maxuser0/minescript/commit/f5b9dc8070e470b1cd7ec7005fd46809e2755f1a))
- Add version-check flags to minescript_runtime.py ([27f65eb](https://github.com/maxuser0/minescript/commit/27f65ebf1d6bfb66ac48602cadc265309751b7fa))
- Specify required deps in paste and minescript_test ([fdeb560](https://github.com/maxuser0/minescript/commit/fdeb5605b6633f35cde3bbf259a8670074cb3b03))
- Add integration test: minescript_test.py ([e2ddd91](https://github.com/maxuser0/minescript/commit/e2ddd913977ea380769bdbc12b43f99b273f4e59))
- Fix breakage in copy command when given 6 params ([6cfdc4b](https://github.com/maxuser0/minescript/commit/6cfdc4bb36a42b492ec71ace1f500cb2232ac243))
- Add changelog for releases through v2.1 ([6da9368](https://github.com/maxuser0/minescript/commit/6da936832ed47dee9dfa13185a70d1a692922bb9))
- Wrap command processing code in try/catch block ([8f05a4b](https://github.com/maxuser0/minescript/commit/8f05a4b32ba0423e14125f35881388c2ed528b14))
- Fix bug when user enters backslash without command ([c3fd241](https://github.com/maxuser0/minescript/commit/c3fd2414801db6c0080dd14d34d5b5d4851fbf31))
- Deprecate minescript.py's exec in favor of execute ([cc3aae7](https://github.com/maxuser0/minescript/commit/cc3aae74097367486a46b170db9efdf0f4731356))
- Use ImmutableList for BUILTIN_COMMANDS ([52f42ac](https://github.com/maxuser0/minescript/commit/52f42acd44d894ac1c54abed5c23032590223314))
- Properly quote args with spaces in error messages ([85d4cf4](https://github.com/maxuser0/minescript/commit/85d4cf4d7621e6e6bee74c877181439541061201))
- Properly quote name, type in entitiesToJsonString ([2d2141a](https://github.com/maxuser0/minescript/commit/2d2141ab66eb618eedec454d6513c4b74e6c93b4))
- Delete obsolete TODOs in copyBlocks(...) ([f4bcbeb](https://github.com/maxuser0/minescript/commit/f4bcbebd6ba83138702d1b50d27964e1f3d146e7))
- Fix bug in command checking when too many params ([cc5a6fa](https://github.com/maxuser0/minescript/commit/cc5a6fa3d55c75152afefd41240659def5819ad2))
- Update Minescript version to 2.1 ([951f90f](https://github.com/maxuser0/minescript/commit/951f90f009d5e714fbccf49cd19d02b777526886))
- Change readBlockState() to level.getBlockState() ([7ea7494](https://github.com/maxuser0/minescript/commit/7ea7494d0c424a217c6a43b4f50758d6ba0d0b0c))
- Fix bug in arg length checking when using varargs ([7b56539](https://github.com/maxuser0/minescript/commit/7b5653900bfb0409b9a973da17c028962e8a7d06))
- Refactor runMinescriptCommand() to use switch/case ([b2227b9](https://github.com/maxuser0/minescript/commit/b2227b9d79c365ebb350666fcf5bf2cf01db1be7))
- Overhaul copy/paste commands with smarter limits ([13e487a](https://github.com/maxuser0/minescript/commit/13e487a6a147f86ddbed2d688be6a7367118d4f2))
- Add script function for intercepting outgoing chat ([4799385](https://github.com/maxuser0/minescript/commit/4799385f360d96ad310c9fd781e2f5fd786454e3))
- Drop ClientChatEvent subscriber from Forge mod ([807d1f5](https://github.com/maxuser0/minescript/commit/807d1f5662f174a5138ee18dfeb6b3c18d045319))
- Replace custom quoteString() with Gson.toJson() ([a2abb1d](https://github.com/maxuser0/minescript/commit/a2abb1dd8e64c7dc2af88652409768e2ab1b466a))
- Change ChunkLoadEventListener to take Runnable ([0ac457a](https://github.com/maxuser0/minescript/commit/0ac457a41b973d490c29e079e914b9739b3447ba))
- Refactor script functions to handleScriptFunction ([18d2ff3](https://github.com/maxuser0/minescript/commit/18d2ff333f6edc477fef57baa38462a416b1f11c))
- Add functions player_name(), players(), entities() ([f99fe52](https://github.com/maxuser0/minescript/commit/f99fe527f802f2940cebecba9e2cc0ffbd5b999b))
- Fix player actions triggered by keyboard and mouse ([be0ef6b](https://github.com/maxuser0/minescript/commit/be0ef6b0cb1dcc2b19dd53b7f6f16e8e064a1bbc))
- Add script functions to trigger player actions ([9ab645a](https://github.com/maxuser0/minescript/commit/9ab645a8b153249555530689ccfd438ac9b9ccc9))
- Sort symbols alphabetically ([8871252](https://github.com/maxuser0/minescript/commit/887125242503a575ad698e627b83b02c10e3910c))
- Simplify flush() implementation ([3c1e650](https://github.com/maxuser0/minescript/commit/3c1e6501e99b17af4924ec7dcf9d0ac383d78d8c))
- Add script function flush() to minescript.py ([a1d068f](https://github.com/maxuser0/minescript/commit/a1d068f0711be54863d95c13f948ccc7fec80d47))
- Add script functions for moving, orienting player ([3053cbc](https://github.com/maxuser0/minescript/commit/3053cbc42237aa9827d5bd6a2f2fa35b0be49614))
- Remove reflection for minecraft.openChatScreen() ([c265a4e](https://github.com/maxuser0/minescript/commit/c265a4ec501dda5b6df8d6ba6e8dc641662bd421))
- Add "screenshot" script function to minescript.py ([26d5ab3](https://github.com/maxuser0/minescript/commit/26d5ab3f63326c39713a1f4d678e809917812516))
- Update Minescript version to 2.0.1 ([ef5a0d7](https://github.com/maxuser0/minescript/commit/ef5a0d7d25d67f2a80b1dad3f48bad561d6f9936))

## v2.0
- Remove old files from original Forge example mod ([2ab835e](https://github.com/maxuser0/minescript/commit/2ab835e1cd54f21253f1b3c0bd052bf9119dacbd))
- Update README files ([474ce9c](https://github.com/maxuser0/minescript/commit/474ce9cf4aa86686bf3cbc17b9980e96969c1bde))
- Exclude vim swap files from jar resources ([70290ba](https://github.com/maxuser0/minescript/commit/70290bab308907da0481eab5afc8d6aaba6a8660))
- Move rename_minecraft_symbols.py to "tools" dir ([9c89cd5](https://github.com/maxuser0/minescript/commit/9c89cd5a41998c4dbb24e222fb56171ba240dc74))
- Add chat-related script functions to minescript.py ([ba009f1](https://github.com/maxuser0/minescript/commit/ba009f184e4f6c215dd43a7b0898d62ac5712c97))
- Add macOS hidden files to .gitignore ([54330dc](https://github.com/maxuser0/minescript/commit/54330dc0ea898e14fed0baa1416561de896a0707))
- Add sources and issues links to Fabric mod info ([899034c](https://github.com/maxuser0/minescript/commit/899034c1fea51692cc48f1fbd7b4b519430ba0a7))
- Automatically populate mod version in version.txt ([927dd54](https://github.com/maxuser0/minescript/commit/927dd5460144f9343280775a2650d21b26b2b04b))
- Normalize build.gradle formatting to 2 spaces/tab ([383b59c](https://github.com/maxuser0/minescript/commit/383b59c24ed8baba4abdf95cb4039c0e287035d9))
- Update all source licenses to GPL-3.0-only ([7cb86fd](https://github.com/maxuser0/minescript/commit/7cb86fdf9d4fb7a91e4543a835fea4b177dcc70b))
- Delete dead build file: applesilicon.gradle ([59c4635](https://github.com/maxuser0/minescript/commit/59c4635fec72bcdf1619e818cc1d723f4321c839))
- Update project version to 2.0 ([05c2191](https://github.com/maxuser0/minescript/commit/05c21918e7b99a35567bbcdfe8344256c3d06375))
- Tighten formatting of mod metadata license string ([09f9140](https://github.com/maxuser0/minescript/commit/09f914057e4a7688221ef82b5614b5fb8b061d7b))
- Update mod metadata license tag to GPL3/MIT ([4b15d40](https://github.com/maxuser0/minescript/commit/4b15d40fecbaad9f23c828d4c22d2d2b868b1f34))
- Change rename_minecraft_symbols.py to MIT license ([3011674](https://github.com/maxuser0/minescript/commit/30116745a4f11bd569f5c531fb485c9f6f4d8b37))
- Add copyright and GPL3 license ID to source files ([f2dd520](https://github.com/maxuser0/minescript/commit/f2dd520ba9172d8fd4977b706412b963d570af19))
- Add docstring to rename_minecraft_symbols.py ([ce6d420](https://github.com/maxuser0/minescript/commit/ce6d420283892277d7895af35aa794f4f5b04cdb))
- Support automatic version compatibility checking ([7e5acf1](https://github.com/maxuser0/minescript/commit/7e5acf174f9c161d2540718d0a03c6b15e50898f))
- Overhaul parsing of script function args with Gson ([580de92](https://github.com/maxuser0/minescript/commit/580de9231e4f8419aa1d420cdedfbb716a6f9dc3))
- Fix "set_nickname" script function ([52e0868](https://github.com/maxuser0/minescript/commit/52e08686c49ce490d59928db4b1388dd91428dad))
- Cleanup obsolete code from chat event overhaul ([ef65d1d](https://github.com/maxuser0/minescript/commit/ef65d1d8f0244c073a86848fec01c5ffc1ae4580))
- Fix onClientChatReceived handler on Fabric & Forge ([fe1decc](https://github.com/maxuser0/minescript/commit/fe1decc8baa95f1332f32da0de164aef1cf4d688))
- Fix crash from script setblock with bad format ([1873d5c](https://github.com/maxuser0/minescript/commit/1873d5c6cb86d723f2d435babe40b7b8f3c12a80))
- Fix undo when overwriting a block multiple times ([c55a846](https://github.com/maxuser0/minescript/commit/c55a846673ae25a223abdcb34b86020aad6ee9e5))
- Clarify non-zero command exit status as error code ([3661bf0](https://github.com/maxuser0/minescript/commit/3661bf0f39161adf54c3614f95648d789fab7e98))
- Support command args with spaces via quotes ([7b5ed09](https://github.com/maxuser0/minescript/commit/7b5ed09c1e9d5fe5375003d98397720d7f7fe763))
- Remove "(minescript) " prefix from log messages ([afddccb](https://github.com/maxuser0/minescript/commit/afddccb6c6699b40522fca7b4ce232457f5d7ebf))
- Garbage collect undo files from previous runs ([d826929](https://github.com/maxuser0/minescript/commit/d826929ff6cfa4fde783583901fee032745b1e50))
- Replace StringBuffer with StringBuilder ([d8f4e25](https://github.com/maxuser0/minescript/commit/d8f4e25c68355925e65080b75e3daaa5274af7e2))
- Clean up chat output for completed jobs ([3ab13e8](https://github.com/maxuser0/minescript/commit/3ab13e8dc65154ee3036d5e3ae558f2ade1ef7d0))
- Reset job id to 1 when there are no running jobs ([87b2f7e](https://github.com/maxuser0/minescript/commit/87b2f7e314c1834ab9c4df037f1488a5e0f65819))
- Cleanup script subprocess termination ([a130d86](https://github.com/maxuser0/minescript/commit/a130d864f2c5a388591640f16b85fc6af0cb1d2c))
- Code cleanup in MinescriptFabricMod ([16b8783](https://github.com/maxuser0/minescript/commit/16b8783dd94379833db41ae66ac546e06ee3a5f6))
- Fix crash from /setblock or /fill with ~ syntax ([ed18b47](https://github.com/maxuser0/minescript/commit/ed18b47868d5b9565001ce0b5117452e05ffc020))
- Write undo file only if command sets blocks ([75a9186](https://github.com/maxuser0/minescript/commit/75a9186f83907f26b4148336125392f375e769ea))
- Add command minescript_log_chunk_load_events ([e265680](https://github.com/maxuser0/minescript/commit/e265680669a5e0ef0e079320ed160f93f0de73bd))
- Add functions player_hand_items, player_inventory ([80c23d4](https://github.com/maxuser0/minescript/commit/80c23d491d3e868817dc2647c99da8adff18c9b7))
- Support new configuration variables via config.txt ([c49ba48](https://github.com/maxuser0/minescript/commit/c49ba483622ca1afd4f2841ccd3a625ea4d82ac3))
- Add "minescript_incremental_command_suggestions" ([bed3231](https://github.com/maxuser0/minescript/commit/bed32316798972be9c12ff74a660d04a519bcff8))

## v1.19.2
- Update help, documentation, and software licensing ([f4edebb](https://github.com/maxuser0/minescript/commit/f4edebb836ddb308a5d41d362cfb20fdb964a6e7))
- Update Minescript logo ([ebed970](https://github.com/maxuser0/minescript/commit/ebed970e33965065a937a67a91675c3c6c7fc9ee))
- Update version to 1.19.2 ([214bcd7](https://github.com/maxuser0/minescript/commit/214bcd754eae0280dffb2fd069a2a4b294a1eded))
- Change build.gradle line endings from DOS to UNIX ([49d4441](https://github.com/maxuser0/minescript/commit/49d4441a7cbfb787b1a022684589746fdcd5ed44))

## v1.19.1
- Add user-friendly Python script function wrappers ([96eaae2](https://github.com/maxuser0/minescript/commit/96eaae25084c895e4c5a6503ebe87667b1cbb4b5))
- Add warning to top of files Minescript overwrites ([af4b19a](https://github.com/maxuser0/minescript/commit/af4b19a6967d9c37086a860c3a7f868743bcb5bf))
- Update metadata: author, license, description ([38e4bea](https://github.com/maxuser0/minescript/commit/38e4bea2278acde1a6093f283e379b5161aec1cf))
- Add paste.py as an auto-installed resource ([1716a7b](https://github.com/maxuser0/minescript/commit/1716a7b2601fd9aa644980a0ddebc4ea36e537bf))
- Add logo file: minescript-logo.png ([38c9dfe](https://github.com/maxuser0/minescript/commit/38c9dfef39940340b6f9674113694e47fda56b28))
- Add versioning for auto-updating minescriptapi.py ([b47ce57](https://github.com/maxuser0/minescript/commit/b47ce5771ab9ce317d78db3b86166b495c74ca0f))
- Print error message when pythonLocation is null ([bd29201](https://github.com/maxuser0/minescript/commit/bd292016e6e97a8a25ac14add5ded40d681f444d))
- Determine Python location strictly from config.txt ([d1c5f01](https://github.com/maxuser0/minescript/commit/d1c5f01cc2a37283ccf327138a73616eb58b160a))
- Update minescriptapi.py for Python 3.9 ([63318bd](https://github.com/maxuser0/minescript/commit/63318bd212190a1230c703600673d755ab1136d2))
- Update to Minecraft 1.19.1 ([9c166db](https://github.com/maxuser0/minescript/commit/9c166db92896903e7a7e1ae0424a2f3c01f77def))
- Cleanup obsolete code: debug logging, onWorldLoad ([e9899cd](https://github.com/maxuser0/minescript/commit/e9899cd47a292ffe2bbbcf1aaa46407985e3b839))
- Change special char for in-game HUD output to pipe ([48d6528](https://github.com/maxuser0/minescript/commit/48d652820f74c157024c92a396bee80a0d1073c1))
- Rename tellrawFormat to formatAsJsonText ([128b773](https://github.com/maxuser0/minescript/commit/128b773f1c8eee2ee08614556e0725e8be45a236))
- Change GUI script output to be client-side only ([37177ca](https://github.com/maxuser0/minescript/commit/37177caba3bbd7e0bc8341e54064021dcc609e8d))
- Update config.txt comment about python location ([703d14f](https://github.com/maxuser0/minescript/commit/703d14f7ed0c363517514160b110e1623e350536))
- Refactor code in run() for finding python location ([ef57018](https://github.com/maxuser0/minescript/commit/ef57018e3a0b5599e05b21a24a39e37952068af1))
- Introduce config.txt for specifying python path ([aaab703](https://github.com/maxuser0/minescript/commit/aaab70384a1806bdd812bc776890291d6d86d6b5))
- Copy minescriptapi.py from jar to file system ([42eb665](https://github.com/maxuser0/minescript/commit/42eb665bb7cb58440dce43dd44cc1adcae242a30))
- Copy minescriptapi.py to fabric jar resources ([25d5d6f](https://github.com/maxuser0/minescript/commit/25d5d6fd0c72ddf09b3524c2fdc1f84ef0822539))
- Initial checkin of Minescript Fabric mod ([ae2cd9b](https://github.com/maxuser0/minescript/commit/ae2cd9b2d6f2a66454258865c39617ce0c929a24))
- Rename "core" dir and MinescriptMod ([243516f](https://github.com/maxuser0/minescript/commit/243516f4ed4f00f0d27ce2ad3be480c3d8714bde))
- Make file paths OS-agnostic ([d7f15be](https://github.com/maxuser0/minescript/commit/d7f15be03c44fc81e3ab01833647e56a807c1eb2))
- Fix server blocklist ([7caf6f7](https://github.com/maxuser0/minescript/commit/7caf6f7afece6b131c09303118df4b22555306af))
- Support killing all jobs with `killjob -1` ([f61cab1](https://github.com/maxuser0/minescript/commit/f61cab1991857faeb996edee7730301b3df00965))
- Let Python scripts execute Minescript commands ([69e7527](https://github.com/maxuser0/minescript/commit/69e7527ce9afbd0d2095252fecd5ae4dcd8dd0af))
- Support labeled copies and pastes ([8df7816](https://github.com/maxuser0/minescript/commit/8df781616d76e2d91bb4dcf638472ab4686c80d6))
- Fix bug: error when terminating Python process ([b8d9321](https://github.com/maxuser0/minescript/commit/b8d93212c8bf0b6fa03a9e2ecd36bb175431daec))
- Stop managing chunk loading/unloaded within mod ([3f5c620](https://github.com/maxuser0/minescript/commit/3f5c620d6f735c73826b2ca9f1f40356d8959d7b))
- Facilitate auto-rewrite between Forge and Fabric ([9cc13e1](https://github.com/maxuser0/minescript/commit/9cc13e1f239c1baf4a04556be0426e7de03b8f88))
- Rename `input` var to `chatEditBox` ([6efd1a5](https://github.com/maxuser0/minescript/commit/6efd1a5ea9b7b5205485f387a58a7e63240f3210))
- Support capture of the enter key ([65a1745](https://github.com/maxuser0/minescript/commit/65a174509c29e72ee6462d8d94fbdb311722923c))
- Refactor into Forge-specific and "core" sources ([0c1fd85](https://github.com/maxuser0/minescript/commit/0c1fd85dd10e0a24383a3983425d384af7e20a54))
- Make copy command size limit configurable ([745f105](https://github.com/maxuser0/minescript/commit/745f105e42903bfc9aa2018b6fbe3950b6711c30))
- Update mod source for Minecraft 1.19 ([b817314](https://github.com/maxuser0/minescript/commit/b81731467f90adfaad560492342751d365bb065b))

## v1.18
- Add script function "set_nickname" ([971ccaf](https://github.com/maxuser0/minescript/commit/971ccafdcb13b329304f96f76032c721c14ee495))
- Support suspend and resume of chunk loading ([d5cbefb](https://github.com/maxuser0/minescript/commit/d5cbefb8203368bd12c233a991d43e29b455b286))
- Implement `await_loaded_region` script function ([9289408](https://github.com/maxuser0/minescript/commit/9289408931b2ea0f2502921b7020359521f094f1))
- Support passing params to script functions ([1f0ac04](https://github.com/maxuser0/minescript/commit/1f0ac04cf366a3fa17ac982db4b7e2573a448a34))
- Support async/streaming script functions ([9ad591b](https://github.com/maxuser0/minescript/commit/9ad591b5b56da30e2ca40da068dd450b24e7a81f))
- Downgrade command signature from json to String ([b4869ab](https://github.com/maxuser0/minescript/commit/b4869abd3d37a3676881639500401c09e4e12d93))
- Support scripts with return values in JSON format ([8c9eaef](https://github.com/maxuser0/minescript/commit/8c9eaefbba948dd09eeff7caf95897c613d2d8ba))
- Fix reading subprocess task's stdout/stderr ([aada724](https://github.com/maxuser0/minescript/commit/aada72467bf65c0f3a9afec23bef980b07afe34f))
- Fix bug in job ID for suspend and resume ([8e2334a](https://github.com/maxuser0/minescript/commit/8e2334a6dafdf8f26cc62766d286f79790af2190))
- Update user-facing exception messages ([8f7ab55](https://github.com/maxuser0/minescript/commit/8f7ab55d8185e3244c16b4b0715d9d329254b6c9))
- Move minescript dir to directly with minecraft dir ([80c44ce](https://github.com/maxuser0/minescript/commit/80c44ce77a73c41333a9fba0424bd0f1cbf11775))
- Delete dead code, add TODOs ([47ab829](https://github.com/maxuser0/minescript/commit/47ab82923ee99b7ec7536f920cdf7db50c1adf9e))
- Drop special prefix handling from enqueueStdout() ([641ea1b](https://github.com/maxuser0/minescript/commit/641ea1b3a96532872a7372a8ef9695b16d2be26c))
- Offload undo queue from memory into files ([42d53f6](https://github.com/maxuser0/minescript/commit/42d53f6cf52e18112e95918d1e5ecd0e8f9ab4e8))
- Implement up/down command history; z -> suspend ([c40593d](https://github.com/maxuser0/minescript/commit/c40593d9b42b2ceadb9dd26be5c98ec49bf7d995))
- Implement "undo" command and undo management ([13f3fe0](https://github.com/maxuser0/minescript/commit/13f3fe05ec080fd88d367efc9aefa1c2f546115a))
- Overhaul of copyBlocks() for "copy" command ([b2cda90](https://github.com/maxuser0/minescript/commit/b2cda9027c8e05581403907e2452a13513f7aec7))
- Allow suspend and resume commands to be nullary ([0e8086b](https://github.com/maxuser0/minescript/commit/0e8086bc6b144b8ff50699ba197addac887289b1))
- Implement "record" command with RecordingTask ([427dae4](https://github.com/maxuser0/minescript/commit/427dae495fedaaef6877ce4c9f15f690869e3b60))
- Split Subprocess into Job, SubprocessTask classes ([c793ad2](https://github.com/maxuser0/minescript/commit/c793ad29a13dacb237689cacc50869a163afc274))
- Move Subprocess.State to JobState ([760a65c](https://github.com/maxuser0/minescript/commit/760a65c477b9678b552ee33c4cb599193e4dcd14))
- Add JobControl interface with yield semantics ([63823ca](https://github.com/maxuser0/minescript/commit/63823ca25a9b34f64d1e2d56410c39ebc5e9a999))
- Support suspend and resume of jobs ([d98a275](https://github.com/maxuser0/minescript/commit/d98a275d0458a7b26e3b0d06e7f401b617e2a3fe))
- Create a separate command queue for each job ([c552b83](https://github.com/maxuser0/minescript/commit/c552b83db7decd51923696489a73428a28d267d8))
- Change substituteMinecraftVars() to copy command ([5c8c700](https://github.com/maxuser0/minescript/commit/5c8c700af52b2ead9426d95cc8305cfe821c3e90))
- Run each Python process from separate Java thread ([2bd665a](https://github.com/maxuser0/minescript/commit/2bd665afe04d44d9f1a3472a388c5dcf0f87715b))
- Relax 100-block distance in "copy" command to 200 ([6883248](https://github.com/maxuser0/minescript/commit/68832484a90f29c28d786b300b071d15c3a1c7f6))
- Refactor common logging to helpers ([73cffce](https://github.com/maxuser0/minescript/commit/73cffce785865cb3a80ced708aae393b1517e98b))
- Switch to custom client-side command dispatching ([3278963](https://github.com/maxuser0/minescript/commit/3278963646acb162b914dc2d3d169897b8e16c48))
- Initial checkin of Minescript Forge mod ([958d711](https://github.com/maxuser0/minescript/commit/958d711a741fe6b02a9d95743642436f1bcea067))
