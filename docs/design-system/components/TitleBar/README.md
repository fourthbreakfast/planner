The bar at the top of every screen: the screen's name in `title`, a seven-segment count beside it, and the controls at the right edge. Both screens use it, so they match.

The count is drawn, not typeset: each digit is seven slanted bars, `digit-width` wide and `digit-height` tall, `digit-gap` apart, in `quiet`, with the unlit bars at about 13% so the digit sits in a faint 8. The title is centered against the digits. Leave `space-3` between them. On the main screen the count is the number of main tasks still open. When subtasks are open too, a second count follows `space-3` further, the same size but fainter: the open subtasks, so the two add up to everything still open. It shows even at 0. On the bin screen it is what the bin holds, and the right edge holds "empty bin" and a back arrow where the bin button is on the main screen.

The consumer provides the title text (lowercase), the number, and the controls.

- Do keep the whole bar one line, `touch` tall.
- Don't set the count in a font, and don't make it larger than the title.
