#!/usr/bin/python3

# SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

"Tool for converting Markdown to HTML."

import markdown
import re
import sys

# If True, rewrite doc link(s) to drop ".md". Otherwise replace ".md" with ".html".
local_html_output = "--local" in sys.argv

html_heading_re = re.compile(r"<h[0-9]>([^<]*)</h[0-9]>")
url_rewrite_re = re.compile(r'(<a\s+href="[^"]+)\.md(#.*)?(">)')

md_input = sys.stdin.read()
html_output = markdown.markdown(md_input, extensions=["fenced_code"])
prev_line = None
for line in html_output.splitlines():
  # The Python implementation of markdown sometimes doesn't output HTML anchors
  # for headings. (Why?) Check the previous line of output for an anchor, and
  # if it's missing, generate one.
  m = html_heading_re.match(line)
  if m:
    title = m.group(1)
    anchor = re.sub("[^a-zA-Z0-9-_]", "", title.lower().replace(" ", "-"))
    anchor_html = f'<p><a name="{anchor}"></a></p>'
    if anchor_html != prev_line:
      print(anchor_html)

  # Add a line linking to the latest docs on GitHub above the table of contents.
  if "<p>Table of contents:</p>" in line:
    print(
        '<p><i>View docs for all versions of Minescript on '
        '<a href="https://github.com/maxuser0/minescript/blob/main/docs/README.md">GitHub</a>'
        '.</i></p>')

  # Rewrite markdown links to web link.
  if local_html_output:
    line = re.sub(url_rewrite_re, r'\1.html\2\3', line)
  else:
    line = line.replace('<a href="README.md', '<a href="docs')
    line = re.sub(url_rewrite_re, r'\1\2\3', line)
  
  line = line.replace("\\", "&#92;")
  print(line)
  prev_line = line
