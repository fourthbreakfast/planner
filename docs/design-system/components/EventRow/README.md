One event on the agenda, with every part of it on view and nothing hidden behind a touch.

At the left, the start time in `title-small` mono inside a `time-column`, with the end time under it in `note` and `quiet` as "– 10:30" when there is one, or "all day" in `note` and `quiet` when there is no time. Beside it, the name in `body`, then the location and then the notes in `body-small` and `quiet`, each on its own line. The row is at least `touch` tall with `space-3` above and below and `gutter` at both sides.

Events sit under a heading for their day: `note`, `quiet`, `space-4` above and `space-1` below, written like "today · sat 31 oct" and plain "sat 31 oct" for other days (the year is added when it is not this one). Soonest day first; inside a day, all-day events come before timed ones. What already happened goes last, under an "earlier" heading with `space-6` above it, newest day first, with names and times in `quiet`.

A touch opens the event for editing. A swipe right moves it to the event bin, with the same undo pill as tasks. There is no swipe left.

The consumer provides the event and whether it is earlier.

- Do show notes in full: the list is meant to be read without opening anything.
- Don't add icons, colour bars or cards.
