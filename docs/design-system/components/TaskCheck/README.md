The check box: a `check` square with sharp corners, outlined in `ink` with `stroke-thin`. Once done, a smaller solid `ink` square fills it, `check-inset` in from the outline, like a lit segment. There is no tick. Tasks, notes, note previews and the notes toolbar all use this one drawing.

Make the touch area `touch` square. Pressing it shrinks the box slightly and gives a short haptic click, on when checking and off when unchecking. Checking a task checks everything inside it; unchecking reopens the tasks above it.

The consumer provides the checked state and what happens on a change.

- Do draw it in `ink`, never in a priority color.
- Don't round its corners or add a tick: round is for the dots, and the block is the whole signal.
