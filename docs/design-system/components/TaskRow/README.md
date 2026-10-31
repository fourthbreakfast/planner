A task: a priority dot at the left, the title, a done/total count when it has subtasks, and a check box at the right.

Lay it out as one row at least `touch` tall, `space-1` of padding at each side. The dot sits in a `dot-lane`; every level of subtask adds `indent` before the dot, so dots and text indent together. Show `done/total` in `note` on the right only when the task has subtasks, and never draw a collapse arrow: touching the row opens or closes what is inside.

The consumer provides the title, the priority, the depth (four levels at most), the subtask counts and whether it is done. A done task is struck through in `quiet`.

- Do mark the targeted row with `aim` for its whole width.
- Do keep the check box 48px square to touch, however small it is drawn.
- Don't size the dot by priority, and don't add a border between rows.
- Don't hide the row's remove and rename gestures behind buttons: swipe right removes, swipe left renames (see SwipeBackground).
