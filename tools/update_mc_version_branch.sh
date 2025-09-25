#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

set -e  # exit on error

branch=$(git branch --show-current)
if [[ "$branch" != mc* ]]; then
  echo "Not a versioned mc branch" >&2
  exit 1
fi

echo "Downloading changes to main branch..." >&2
git checkout main && git pull && git checkout $branch

echo "Merging changes from main branch to $branch..." >&2
git merge --no-commit --no-ff main

# Grab Minescript version updated from main:
new_version=$(grep '^version=' gradle.properties)

echo "Reverting version changes in build.gradle and gradle.properties..." >&2
git checkout HEAD -- build.gradle
git checkout HEAD -- gradle.properties

echo "Re-applying new Minescript version from main: $new_version" >&2
sed -i "" "s/^version=.*/$new_version/" gradle.properties
git add gradle.properties

echo "update_mc_version_branch.sh: Done." >&2
