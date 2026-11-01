The marks that show where a dragged task will land: a 2px `ink` line along the top or bottom edge of a row, or the whole row lit with `aim` for "nest inside this one". The dragged row itself follows the finger at 92% opacity, on `aim`, with the `lift` shadow.

The line starts where the dropped task's dot would sit: `space-1` plus one `indent` for each level of depth. Every place means one thing, and sideways movement means nothing: the middle of a row puts the task inside it, at the end, and a line between two rows puts it just above the lower one, at that row's depth. In priority mode the task then snaps back among its own colour: dropped above all the reds, a yellow becomes the first yellow. The line shows that place and not the finger's: it stays at the top of the yellows however far up the task is dragged. A small haptic tick fires every time the drop spot changes.

The invisible row at the end of the list (`end-row` tall) takes anything dropped far down and makes it top-level and last, drawn as a line at the left edge.

The consumer provides the drop target, the depth and the zone (before, inside or after).

- Do draw the line over the row, never between rows, so nothing shifts.
- Don't use a priority color for any of these.
