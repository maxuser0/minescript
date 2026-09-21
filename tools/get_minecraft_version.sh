#!/bin/zsh
# SPDX-FileCopyrightText: © 2026 Minescript Contributors
# SPDX-License-Identifier: MIT

set -e

if [[ ! -f gradle.properties ]]; then
  echo "Error: cannot find gradle.properties to identify Minecraft version." >&2
  exit 1
fi

mc_version=$(cat gradle.properties |grep '^minecraft_version=' |sed 's/.*=//')
echo $mc_version
