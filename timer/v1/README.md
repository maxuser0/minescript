## `timer v1`

Sends a message or executes a command at the specified time.

&nbsp;

**Requirements**

- Minescript v2.0 or higher
- `eval` action requires `eval.py`

&nbsp;

**Usage**

```
\timer <time> (chat|echo|execute|eval) <messageOrCommand>
```

`<time>` can be a countdown in hours, minutes, or seconds or a time
of day.  Supported formats include: `1:23am`, `1:23 PM`,
`13:23`, `10s` (seconds), `5m` (minutes), `2h` (hours). Add `*`
to countdown times to repeat, e.g.  `5m*` repeats every 5
minutes.  Add `*` and a number to repeat that number of times
at the given time interval.

&nbsp;

**Examples**

Send a chat at 12 noon:

```
\timer 12pm chat "FYI: it is now noon"
```

Send a message to yourself at 12:30:

```
\timer 12:30pm echo "note to self: time to eat lunch"
```

Copy blocks labeled "timed_copy" in 2 minutes:

```
\timer 2m execute  "\\copy ~ ~ ~ ~64 ~64 ~64 timed_copy"
```
*(note the double backslash before `copy`; this is needed to escape the
backslash within double quotes.)*

Set game time to midday every hour:

```
\timer 1h* execute "/time set day"
```

Take 3 screenshots 5 seconds apart:

```
\timer 5s*3 eval "screenshot()"
```
*(note: screenshot() added in Minescript v2.1)*
