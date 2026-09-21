#!/bin/zsh
# SPDX-FileCopyrightText: © 2026 Minescript Contributors
# SPDX-License-Identifier: MIT

set -e

loader=${1:?Usage: minescript_dev_loader.sh <fabric|neoforge>}

# Strip optional trailing "/" in case tab completion completed
# the name of a matching directory.
loader="${loader%/}"

if [[ ! -f gradle.properties ]]; then
  echo "Error: cannot find gradle.properties to identify Minecraft version." >&2
  exit 1
fi

mc_version=$(cat gradle.properties |grep '^minecraft_version=' |sed 's/.*=//')

minescript_version=$(cat gradle.properties |grep '^version=' |sed 's/.*=//')

loader_display_name=$(echo $loader |sed 's/f/F/' | sed 's/n/N/')

echo "Building Minescript $minescript_version for $loader_display_name on Minecraft $mc_version ..." >&2
./gradlew $loader:build

echo "Loading Minescript $minescript_version for $loader_display_name on Minecraft $mc_version ..." >&2
./tools/minecraft_mod_loader.sh $loader $mc_version && open -a Minecraft
