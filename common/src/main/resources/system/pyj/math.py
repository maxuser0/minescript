# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""math v5.0 distributed via Minescript jar file

Minescript math module for Pyjinn scripts, modeled after the Python math module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

_Math = JavaClass("java.lang.Math")
_Numbers = JavaClass("net.minescript.common.Numbers")

pi = _Math.PI
e = _Math.E
tau = _Math.TAU

def sqrt(x):
  return _Math.sqrt(x.doubleValue())

def fmod(x, y):
  return _Numbers.javaMod(x, y)