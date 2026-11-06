The form for one event. Its title, "event", uses the segmented screen heading, with a back arrow at the right and "delete" beside it only when the event already exists.

The fields are plain, with no hint sentences, no rules and no borders: a `note` label in `quiet` above each one, then the field. Name, location and notes are typed on the page colour, like the add line. Day and time are shown as plain text values that you touch; both are empty until chosen. A day left empty means today when saved, and a time left empty means the whole day. Notes start one line tall and grow as you write.

Save is a full-width button at the bottom, above the keyboard: `touch` tall, `gutter` from the edges, on `panel` with a `stroke-thin` `line` border and `radius-check` corners, the word in `title-small` and `ink`. It is faint until the event has a name. It is the one shape on the screen that looks pressable, and it is still quiet: no fill in `ink`, no colour.

The choosers sit on a `panel` with a `line` border and `radius-check` corners. The day chooser is a month of numbers with arrows to change month, the chosen day filled with `ink` like a checked box and today outlined. The time chooser has three choices across the top, "all day", "start" and "start – end", the one chosen in `ink`. With a start it shows the time in `title`, then an hour grid and a minute grid in five minute steps, each cell a rounded square, the chosen one filled with `ink`. With an end, touch the start or the end time to choose which one the grids change. An end is always after the start.

Under the time, once a start time is set, there is a `reminder` line: empty until chosen, then "at the time", "10 minutes before" or "1 hour before". It opens the same quiet panel as the choosers with one line to each choice and the chosen one in `ink`; touching one picks it and closes the panel. The first reminder asks the phone for permission to show notifications. A reminder is a notification with the event's name and its time (and place), at that moment; it is set again after a restart.

The consumer provides the event, if there is one, and what saving and deleting do.

- Do keep every control the same size and look as the rest of the app; there is no dial and no platform picker.
- Don't add hint text, icons, rules or colour to the fields.
