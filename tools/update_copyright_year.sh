#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

# Update copyright year(s) in source files.
#
# Usage:
#   tools/update_copyright_year.sh [-n] OLD_YEARS NEW_YEARS
#
# -n: dry run which prints, but does not execute, git commands
#
# Example:
#   ./tools/update_copyright_year.sh -n 2022-2025 2022-2026  # dry run
#   ./tools/update_copyright_year.sh 2022-2025 2022-2026  # real run

dry_run=0
if [ "$1" = "-n" ]; then
  dry_run=1
  shift
fi

old_years=${1:?"Error: old year(s) required as first arg"}
new_years=${2:?"Error: new year(s) required as second arg"}

for file in $(git ls-files \
    |grep -v '\.git' \
    |grep -v '/run/' \
    |grep -v '/build/' \
    |grep -v '/\.gradle/' \
    |grep -v '\.swp$' \
    |grep -v '\.png$' \
    |xargs grep -l " $old_years "); do
  if [ $dry_run = 1 ]; then
    grep -H " $old_years " $file || ls $file
  else
    sed -i '' -e "s/ $old_years / $new_years /" $file
  fi
done
