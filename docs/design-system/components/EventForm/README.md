The form for one event, called "event" in `title`, with a back arrow at the right of the bar and "delete" beside it only when the event already exists. Save is a full-width button at the bottom, above the keyboard: `touch` tall, `gutter` from the edges, on `panel` with a `stroke-thin` `line` border and `radius-check` corners, the word in `title-small` and `ink`. It is faint until the event has a name. It is the one shape on the screen that looks pressable, and it is still quiet: no fill in `ink`, no colour.

Every field is a writing line, like the line you sign on: a `note` label in `quiet`, then an empty line at least `touch` tall with the text resting right on top of a `stroke-thin` rule in `quiet` (at 60%), `gutter` in from both edges. The text sits `space-1` above the rule, never in the middle of the row, so the rule reads as something to write on and not as a divider. There is no hint text inside the fields. Name, location and notes are typed; day and time are touched and open a chooser. A day left empty means today when saved, and a time left empty means the whole day.

The choosers sit on a `panel` with a `line` border and `radius-check` corners. The day chooser is a month of numbers with arrows to change month, the chosen day filled with `ink` like a checked box and today outlined. The time chooser has three choices across the top, "all day", "start" and "start – end", the one chosen in `ink`. With a start it shows the time in `title`, then an hour grid and a minute grid in five minute steps, each cell a rounded square, the chosen one filled with `ink`. With an end, touch the start or the end time to choose which one the grids change. An end is always after the start.

The consumer provides the event, if there is one, and what saving and deleting do.

- Do keep every control the same size and look as the rest of the app; there is no dial and no platform picker.
- Don't add hint text, icons or colour to the fields.
