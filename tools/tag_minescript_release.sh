#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

# Update tag for Minescript and Minecraft versions in the current branch and
# pushes it to the git origin.
#
# Usage:
#   tools/tag_minescript_release.sh [-n] [COMMIT_HASH]
#
# -n: dry run which prints, but does not execute, git commands
#
# If COMMIT_HASH is specified, apply the tag to the corresponding commit.
# Otherwise apply the tag to the latest commit on this branch.
#
# Generates tags like: "v5.0b7-mc1.21.8"

set -e

dry_run_prefix=
if [[ $1 = "-n" ]]; then
  dry_run_prefix=echo
  shift
fi

commit_hash="$1"

if [[ ! -f gradle.properties ]]; then
  echo "Error: cannot find gradle.properties to identify Minecraft version." >&2
  exit 1
fi

mc_version=mc$(cat gradle.properties |grep '^minecraft_version=' |sed 's/.*=//')
if [[ ! "$mc_version" =~ ^mc1\..* ]]; then
  echo "Error: mc_version does not begin with 'mc1.': $mc_version" >&2
  exit 2
fi

minescript_version=$(cat gradle.properties |grep '^version=' |sed 's/.*=//')

hash_message=""
if [[ -n $commit_hash ]]; then
  hash_message=" at commit $commit_hash"
fi

echo "Tag Minescript $minescript_version for Minecraft $mc_version$hash_message? [y/N]"
read input

if [[ $input = "y" ]]; then
  echo Tagging with commit $hash_message
  tag=v${minescript_version}-${mc_version}
  $dry_run_prefix git tag $tag -a -m "Release of Minescript ${minescript_version} for ${mc_version}" $commit_hash
  $dry_run_prefix git push origin $tag
else
  echo 'Canceled.'
fi
