The bar at the top of every screen: the screen's name in `title`, a seven-segment count beside it, and the controls at the right edge. Both screens use it, so they match.

The count is drawn, not typeset: each digit is seven slanted bars, `digit-width` wide and `digit-height` tall, `digit-gap` apart, in `quiet`, with the unlit bars at about 13% so the digit sits in a faint 8. The title is centered against the digits. Leave `space-3` between them. On the main screen the count is the number of main tasks still open. When subtasks are open too, a second count follows `space-3` further, the same size but fainter: the open subtasks, so the two add up to everything still open. It is left out when there are none. On the bin screen it is what the bin holds, and the right edge holds "empty bin" and a back arrow where the bin button is on the main screen.

A magnifying glass, drawn with two `stroke-thin` lines in `quiet` on a `touch` square, sits left of the bin on every tab and opens the search.

A bin has the tab row too: the word "bin" in the calculator lettering, in the place of the tab it was opened from (left for tasks, middle for the agenda, right for notes). Under it is the same bar as the main screen, with only the count at the left and the back arrow where the bin button was.

$1 (lowercase), the number, and the controls.

- Do keep the whole bar one line, `touch` tall.
- Don't set the count in a font, and don't make it larger than the title.
