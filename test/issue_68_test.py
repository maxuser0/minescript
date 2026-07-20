"""Regression test for https://github.com/maxuser0/minescript/issues/68."""

import sys
from pathlib import Path


resources = Path(__file__).parents[1] / "common" / "src" / "main" / "resources"
sys.path.insert(0, str(resources))

try:
  import system.pyj.minescript  # noqa: E402,F401
except ImportError as e:
  expected = "system.pyj.minescript is only available to Pyjinn scripts (.pyj)"
  assert expected in str(e), str(e)
else:
  raise AssertionError("CPython unexpectedly imported the Pyjinn-only Minescript module")
