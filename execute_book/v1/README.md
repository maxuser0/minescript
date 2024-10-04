## `execute_book v1`

Executes the contents of a book interpreted as Python code.

&nbsp;

**Requirements**

  Minescript v2.0 or higher

&nbsp;

**Usage**

```
\execute_book
\execute_book - [<args>]
\execute_book <title> [<args>]
```

`<args>` passed to the book-based script can be read as `sys.argv`.

&nbsp;

**Examples**

Executes the book in the player's hand with no args:

```
\execute_book
```

Executes the book in the player's hand with args `foo` and `bar`:

```
\execute_book - foo bar
```

Executes a book in the player's inventory with the title
"my python code" passing no args:

```
\execute_book "my python code"
```

Executes a book in the player's inventory with the title
"my python code" passing args `foo` and `bar`:

```
\execute_book "my python code" foo bar`
```
