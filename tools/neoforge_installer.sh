#!/bin/bash
# SPDX-FileCopyrightText: © 2026 Minescript Contributors
# SPDX-License-Identifier: MIT

set -e
shopt -s nullglob

if [[ -z $MINESCRIPT_MOD_INSTALLERS_DIR ]]; then
  echo "Error: \$MINESCRIPT_MOD_INSTALLERS_DIR is not defined. E.g. define it as:" >&2
  echo "export MINESCRIPT_MOD_INSTALLERS_DIR=\"$HOME/mod-installers\"" >&2
  exit 1
fi

# Use a command guard for echo-only replacement of commands that normally
# have side effects.
side_effect=
if [[ $1 = "-n" ]]; then
  side_effect="echo DRY_RUN:"
fi

mc_version=$(./tools/get_minecraft_version.sh)

cd "$MINESCRIPT_MOD_INSTALLERS_DIR"

# Drop the leading "1." from the MC version when determining NeoForge version.
neoforge_version="${mc_version#1.}"

# MC snapshots are named like 26.1-snapshot-10 while NeoForge versios are named
# like neoforge-26.1.0.0-alpha.13+snapshot-10. So recognize an additional
# pattern for snapshots.
if [[ "$mc_version" =~ (.*)-snapshot-([0-9]+) ]]; then
  # BASH_REMATCH[1] is everything before "-snapshot-"
  # BASH_REMATCH[2] is the integer snapshot version
  neoforge_version="${BASH_REMATCH[1]}.*+snapshot-${BASH_REMATCH[2]}"
  jar_pattern="neoforge-${neoforge_version}-installer.jar"
else
  neoforge_version="${mc_version#1.}"
  jar_pattern="neoforge-$neoforge_version.*-installer.jar"
fi

matching_jars=( $jar_pattern )
num_matching_jars=${#matching_jars[@]}

if [[ $num_matching_jars = 0 ]]; then
  echo "No NeoForge installer jars for MC $mc_version matching \"$jar_pattern\":" >&2
  ls neoforge-*-installer.jar >&2
  exit 1
elif [[ $num_matching_jar > 0 ]]; then
  echo "Multiple matching NeoForge installer jars for MC $mc_version:" >&2
  echo $matching_jars >&2
  exit 2
else
  installer_jar="${matching_jars[0]}"
  echo "Found NeoForge installer jar for MC $mc_version: $installer_jar"
  $side_effect java -jar $installer_jar --installClient
fi
