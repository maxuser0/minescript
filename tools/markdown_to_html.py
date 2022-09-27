#!/usr/bin/python3

# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

"Tool for converting Markdown to HTML."

import markdown
import re
import sys

html_heading_re = re.compile(r"<h[0-9]>([^<]*)</h[0-9]>")

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
  print(line)
  prev_line = line
