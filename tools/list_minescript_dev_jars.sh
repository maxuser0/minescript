#!/bin/zsh
# SPDX-FileCopyrightText: © 2026 Minescript Contributors
# SPDX-License-Identifier: MIT

set -e

ls "$@" */build/libs/minescript-*-$(./tools/get_minecraft_version.sh)-$(./tools/get_minescript_version.sh).jar
