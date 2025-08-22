# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""sys v5.0 distributed via Minescript jar file

Minescript sys module for Pyjinn scripts, modeled after the Python sys module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

_System = JavaClass("java.lang.System")

argv = __script__.vars["sys_argv"]
version = __script__.vars["sys_version"]
stdout = _System.out
stderr = _System.err

def exit(status: int = None):
  __exit__(status)