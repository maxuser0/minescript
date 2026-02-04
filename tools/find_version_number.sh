#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

if [ "$1" = "-l" ]; then
  grep_flags="-l"
  shift
fi
version=${1:?"Error: version number required."}
if [ "$2" = "-l" ]; then
  grep_flags="-l"
  shift
fi

find common -type f |
    grep -v /.gradle/ |
    grep -v /.git/ |
    grep -v /build/ |
    grep -v /run/ |
    grep -v /caches/ |
    grep -v /archive/ |
    grep -v '/\..*\.swp$' |
    xargs grep -F $grep_flags "$version" |grep -v "[.0-9]${version}" |grep -v "${version}[.0-9]" |
    sed 's/^\.\///'
