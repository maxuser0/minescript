# SPDX-FileCopyrightText: © 2024-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""atexit v5.0 distributed via Minescript jar file

This library provides a subset of the functionality of the Python atexit module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

def register(func, *args, **kwargs):
  __atexit_register__(func, *args, **kwargs)
  return func

def unregister(func):
  __atexit_unregister__(func)