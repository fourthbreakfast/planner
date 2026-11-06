A task: a priority dot at the left, the title, a done/total count when it has subtasks, and a check box at the right.

Lay it out as one row at least `touch` tall, `space-1` of padding at each side. The dot sits in a `dot-lane`; every level of subtask adds `indent` before the dot, so dots and text indent together. Show `done/total` in `note` on the right only when the task has subtasks, and never draw a collapse arrow: touching the row opens or closes what is inside.

The consumer provides the title, the priority, the depth (four levels at most), the subtask counts and whether it is done. A done task is struck through in `quiet`.

- Do mark the targeted row with `aim` for its whole width.
- Do keep the check box 48px square to touch, however small it is drawn.
- Keep the priority size steps (10, 8 and 6dp diameters); do not add borders between task rows.
- On the phone, hold before swiping right to remove or left to rename. Desktop also exposes context menus and keyboard shortcuts.
