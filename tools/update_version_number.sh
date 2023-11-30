#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

# Updates the Minescript version number across configs and sources. If
# --fork_docs is specified, docs/README.md is forked to
# docs/v<old_version>/README.md; if --nofork_docs is specified, docs are not
# forked. Pass -n for a dry run, i.e. print the commands that would run or the
# old version strings that would be matched, but do not rewrite the version
# number.
#
# This script must be run from the 'minescript' directory, and expects 'fabric'
# and 'forge' subdirectories.
#
# Usage:
#   tools/update_version_number.sh <old_version> <new_version> --fork_docs|--nofork_docs|--fork_docs_only -n
#
# Example:
#   Update version number from 2.1 to 2.2 and fork docs from to docs/v2.1:
#   $ tools/update_version_number.sh 2.1 2.2 --fork_docs

old_version=${1:?"Error: old version number required."}
new_version=${2:?"Error: new version number required."}
fork_docs_arg=${3:?"Error: must specify --fork_docs or --nofork_docs or --fork_docs_only"}

# Discard the fixed-position args.
shift 3

fork_docs_only=0
if [[ $fork_docs_arg = "--fork_docs_only" ]]; then
  fork_docs=1
  fork_docs_only=1
elif [[ $fork_docs_arg = "--fork_docs" ]]; then
  fork_docs=1
elif [[ $fork_docs_arg = "--nofork_docs" ]]; then
  fork_docs=0
else
  echo "Required 3rd arg must be --fork_docs or --nofork_docs." >&2
  exit 1
fi

dry_run=0

while (( "$#" )); do
  case $1 in
    -n)
      dry_run=1
      ;;
    *)
      echo "Unrecognized arg: $1"  >&2
      exit 2
      ;;
  esac
  shift
done

old_version_re=$(echo $old_version |sed 's/\./\\./g')

if [ "$(basename $(pwd))" != "minescript" ]; then
  echo "update_version_number.sh must be run from 'minescript' directory." >&2
  exit 3
fi

function check_subdir_exists {
  subdir="$1"
  if [ ! -d "$subdir" ]; then
    echo "update_version_number.sh cannot find '${subdir}' subdirectory." >&2
    exit 4
  fi
}

check_subdir_exists fabric
check_subdir_exists forge
check_subdir_exists docs

if [ ! -e docs/README.md ]; then
  echo "Required file missing: docs/README.md" >&2
  exit 5
fi

if [ $fork_docs = 1 ]; then
  old_version_docs=docs/v${old_version}
  if [ $dry_run = 0 ]; then
    mkdir "$old_version_docs" || (echo "$old_version_docs already exists." >&2; exit 6)

    old_version_readme=$old_version_docs/README.md
    cp -p docs/README.md "$old_version_readme"
  else
    echo mkdir "$old_version_docs" || (echo "$old_version_docs already exists." >&2; exit 7)
  fi
fi

# Rewrite version in first line of docs/README.md.
if [ $dry_run = 0 ]; then
  sed -i '' -e \
      "s/^## Minescript v${old_version} docs$/## Minescript v${new_version} docs/" \
      docs/README.md
else
  grep "^## Minescript v${old_version} docs$" docs/README.md
fi

if [ $fork_docs_only = 0 ]; then
  for x in {fabric,forge}/gradle.properties; do
      if [ $dry_run = 0 ]; then
        sed -i '' -e "s/mod_version=${old_version_re}$/mod_version=${new_version}/" $x
      else
        grep -H "$old_version_re" $x
      fi
  done

  for x in $(tools/find_version_number.sh $old_version -l |grep '\.py$'); do
      if [ $dry_run = 0 ]; then
        sed -i '' -e "s/v${old_version_re} /v${new_version} /" $x
      else
        grep -H "v${old_version_re} " $x
      fi
  done
fi
