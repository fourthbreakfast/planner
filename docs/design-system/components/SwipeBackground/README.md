What shows under a row as it is pulled sideways. A row only swipes after a finger has rested on it for a moment; a sideways sweep without the hold switches tabs. Pull right: a `priority-high` field with "move to bin" in `on-accent` at the left edge; letting go past a quarter of the width moves the task and everything inside it to the bin and shows the undo pill. Pull left: an `aim` field with "rename" in `ink` at the right edge; letting go past a quarter of the width puts the title into edit in place and the row springs back.

Both labels are `label`, lowercase, at `gutter` from the edge. Swiping is off while dragging or renaming.

The consumer provides the labels and the two actions. Also offer both as accessibility actions on the row.

- Do keep the two directions to one job each.
- Don't add a confirmation: undo is the safety net, and the bin keeps what was removed.
- White `on-accent` on `priority-high` is 4.5:1 or better, so the label meets the floor at 14px medium.
