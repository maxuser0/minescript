### chessbot v1

*Requires:*

- `minescript v4.0`

Required commands: setblock, summon, tp

*Usage:*

```
\chessbot init - Reset the board that the player is looking at, otherwise create a new board.
\chessbot play - Start playing a game at the board the player is looking at.
\chessbot export - Print game state in Forsyth-Edwards Notation for game that player is viewing.
```

`play` by default is a local human-vs-human game. The following modes are also supported as an
additional param in the form of a string of length 2 where the first is the white-player mode and
the second the black-player mode:

- `h`: human player (with move validation)
- `m`: manual human player (no move validation)
- `c`: computer player

e.g. `\chessbot play hc` starts a game with a local human player as white and a local computer
player as black.

Right-click on a piece in the player's crosshairs to select it. Then move the selected piece by
right-clicking again on another piece or square.
