#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

set -e  # exit on error

branch=$(git branch --show-current)
if [[ "$branch" != mc* ]]; then
  echo "Not a versioned mc branch" >&2
  exit 1
fi

echo "Fetching latest changes from origin..." >&2
git fetch origin main:main 2>/dev/null || git fetch origin main || true

echo "Cherry-picking non-version-specific commits from main to $branch..." >&2
for commit in $(git rev-list --reverse --no-merges --cherry-pick --right-only HEAD...main); do
  subject=$(git log -1 --format=%s "$commit")

  # Skip Minecraft version-specific commits:
  if [[ "$subject" =~ ^Update\ (MC|Minecraft|mc[0-9]) ]]; then
    echo "Skipping version-specific commit $commit: $subject" >&2
    continue
  fi

  # Skip if already cherry-picked (recorded by -x in commit message):
  if git log -n 100 --grep="cherry picked from commit $commit" --format=%H | grep -q .; then
    echo "Commit $commit was already cherry-picked; skipping." >&2
    continue
  fi

  echo "Applying $commit: $subject" >&2
  if ! git cherry-pick -x "$commit"; then
    if git status | grep -q "nothing to commit"; then
      echo "Commit $commit is empty or already applied; skipping." >&2
      git cherry-pick --skip
    else
      echo "Conflict while cherry-picking $commit: $subject" >&2
      echo "Resolve conflicts, then run 'git cherry-pick --continue'." >&2
      exit 1
    fi
  fi
done

echo "update_mc_version_branch.sh: Done." >&2
