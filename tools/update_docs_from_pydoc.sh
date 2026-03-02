#!/bin/bash

# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

# Updates the detailed documentation file at docs/README.md from the
# pydoc in minescript.py file.

README="docs/README.md"
SOURCE="common/src/main/resources/system/lib/minescript.py"

# Delete everything from the "### minescript module" line to the end of the file.
# On macOS (darwin), sed -i '' is used for in-place editing without a backup file.
sed -i '' '/### minescript module/,$d' "$README"

./tools/pydoc_to_markdown.py < "$SOURCE" >> "$README"
