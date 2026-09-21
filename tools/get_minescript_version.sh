#!/bin/zsh
# SPDX-FileCopyrightText: © 2026 Minescript Contributors
# SPDX-License-Identifier: MIT

set -e

if [[ ! -f gradle.properties ]]; then
  echo "Error: cannot find gradle.properties to identify Minescript version." >&2
  exit 1
fi

minescript_version=$(cat gradle.properties |grep '^version=' |sed 's/.*=//')

echo $minescript_version
