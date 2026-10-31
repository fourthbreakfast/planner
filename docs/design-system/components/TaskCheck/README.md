The check box: an 18px square with a `radius-check` corner, outlined in `ink` when open and filled with `ink` and a `page`-colored tick when done.

Make the touch area `touch` square. Pressing it shrinks the box slightly and gives a short haptic click, on when checking and off when unchecking. Checking a task checks everything inside it; unchecking reopens the tasks above it.

The consumer provides the checked state and what happens on a change.

- Do draw it in `ink`, never in a priority color.
- Don't make it round: round is for the dots.
