# SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: MIT

r"""execute_book v1 distributed via minescript.net

Executes the contents of a book interpreted as Python code.

Requires:
  minescript v2.0

Usage:
  \execute_book
  \execute_book - [<args>]
  \execute_book <title> [<args>]

If <title> is given, searches player's inventory for a book with
that title. Otherwise uses a book in the player's hand.

<args> passed to the book-based script can be read as
`sys.argv`.

Examples:
  Executes the book in the player's hand with no args:
  \execute_book

  Executes a book in the player's hand with args "foo" and "bar":
  \execute_book - foo bar

  Executes a book in the player's inventory with the title
  "my python code" passing no args:
  \execute_book "my python code"

  Executes a book in the player's inventory with the title
  "my python code" passing args "foo" and "bar":
  \execute_book "my python code" foo bar
"""

import json
import minescript
import re
import shlex
import sys

# Dict from book types to their default item names.
book_types = {
  "book": "Book",
  "writable_book": "Book and Quill",
  "written_book": "Written Book"
}

# TODO(maxuser): The NBT processing in this script is very hacky and brittle.
# Use a library for parsing NBT data properly.

def nbt_string_to_tokens(nbt_str):
  "Tokenizes an NBT string into a list of string tokens."
  lex = shlex.shlex(nbt_str, posix=True)
  lex.escapedquotes = lex.quotes  # escape single quotes, too
  return [tok for tok in lex]


def get_book_pages(item):
  """Gets pages of an item if it's a book.

  Args:
    item: dict of item attributes

  Returns:
    [str] of pages if item is a book, None otherwise
  """
  if item and item["item"] in book_types:
    nbt = item.get("nbt")
    if nbt:
      tokens = nbt_string_to_tokens(nbt)
      if "pages" in tokens:
        pages_pos = tokens.index("pages")
        if (pages_pos + 3 < len(tokens) and
            tokens[pages_pos + 1] == ":" and
            tokens[pages_pos + 2] == "["):
          pages_with_delimiters = tokens[pages_pos + 3:]
          pages = []
          for i, tok in enumerate(pages_with_delimiters):
            if i % 2 == 1:
              if tok == "]":
                return pages
              if tok != ",":
                # Error: expected comma at odd position delimiting pages in NBT data.
                return pages
              continue
            if tok.startswith("{"):
              text = json.loads(tok)["text"]
              pages.append(text)
            else:
              pages.append(tok)
    return []  # book with no pages
  return None


def execute_book_pages(pages):
  """Executes the contents of a book's pages as Python code.

  Args:
    pages: [str] representing pages of a book interpreted as Python code
  """
  exec("\n".join(pages))


def get_book_title(nbt_str):
  """Computes the title of a book.

  Args:
    nbt_str: NBT string in which to look for a book title

  Returns:
    Book title found in nbt_str, otherwise None
  """
  title = None
  if nbt_str:
    rename_tok_prefix = ["display", ":", "{", "Name", ":"]
    title_tok_prefix = ["title", ":"]
    filtered_title_tok_prefix = ["filtered_title", ":"]
    nbt_tokens = nbt_string_to_tokens(nbt_str)
    for i in range(len(nbt_tokens) - len(rename_tok_prefix) - 2):
      if nbt_tokens[i : i + len(rename_tok_prefix)] == rename_tok_prefix:
        m = re.fullmatch('{"text":"(.*)"}', nbt_tokens[i + len(rename_tok_prefix)])
        if m:
          # The renamed title is the one displayed in inventory. So prefer this if found.
          return m.group(1)
      elif nbt_tokens[i : i + len(title_tok_prefix)] == title_tok_prefix:
        # Don't return yet. There may still be a renamed title.
        title = nbt_tokens[i + len(title_tok_prefix)]
      elif nbt_tokens[i : i + len(filtered_title_tok_prefix)] == filtered_title_tok_prefix:
        # Don't return yet. There may still be a renamed title.
        title = nbt_tokens[i + len(filtered_title_tok_prefix)]
  return title


def execute_book_with_title(title):
  """Executes the book in the player's inventory that matches the given title.

  If zero or multiple books are found with the given title, do nothing.
  """
  matching_books = []
  for i, item in enumerate(minescript.player_inventory()):
    book = get_book_pages(item)
    default_title = book_types.get(item["item"])
    if book is not None and (get_book_title(item.get("nbt")) or default_title) == title:
      matching_books.append(book)

  if len(matching_books) == 0:
    print(f'No books found in inventory with title "{title}".', file=sys.stderr)
  elif len(matching_books) > 1:
    print(
        f'{len(matching_books)} books found in inventory with title "{title}".',
        file=sys.stderr)
  else:
    book = matching_books[0]
    if book:
      execute_book_pages(book)
    else:
      print(f"{repr(title)} is empty.", file=sys.stderr)


def execute_book_in_hand():
  "If the player is holding a book, execute its content as Python code."
  for item in minescript.player_hand_items():
    book = get_book_pages(item)
    if book is not None:
      if book:
        default_title = book_types.get(item["item"])
        sys.argv[0] = get_book_title(item.get("nbt")) or default_title
        execute_book_pages(book)
      else:
        print(f"Book in player's hand is empty.", file=sys.stderr)
      return
  print(f"No book in player's hand.", file=sys.stderr)


def main():
  title = None
  if len(sys.argv) == 1:
    sys.argv = ["-"]
  else:
    if sys.argv[1] != "-":
      title = sys.argv[1]
    sys.argv = sys.argv[1:]

  if title:
    execute_book_with_title(title)
  else:
    execute_book_in_hand()


if __name__ == "__main__":
  main()
