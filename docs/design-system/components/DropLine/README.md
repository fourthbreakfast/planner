The marks that show where a dragged task will land: a 2px `ink` line with a small leading dot along an insertion edge, or the whole row lit with `aim` for "nest inside this one". A translucent, noninteractive text preview follows the pointer; the original row stays in place at reduced opacity so the list geometry remains stable.

The line starts at the selected tree depth. The middle of a row nests inside it. Near an edge, left and right travel selects whether the dropped task sits beside that row or beside one of its enclosing branches. Moving right below a row may append a child; moving left can move outside a parent without splitting its subtree. Small dead bands around zone and depth boundaries prevent flicker.

Priority still ranks siblings: dropped above all the reds, a yellow becomes the first yellow. The line shows the actual ranked destination, rather than promising the pointer's unranked position. A short inside/before/after caption names the destination and reminds people that horizontal travel chooses the level. The caption has a polite accessibility announcement. A small haptic tick fires when the phone's destination changes.

The end area appends at the selected level of the final branch; pulling fully left makes a main task. Near viewport edges, scrolling accelerates gradually. The desktop gesture is owned by the list so the source row scrolling out of view cannot cancel it. Escape on desktop or Back on the phone cancels the drag.

The consumer provides the drop target, the depth and the zone (before, inside or after).

- Do draw the line over the row, never between rows, so nothing shifts.
- Don't use a priority color for any of these.
- Draw the insertion line above the text preview, so the preview cannot conceal it.
- Disable reparenting in the isolated shortlist: omitted ancestors are context, not valid tree boundaries.
