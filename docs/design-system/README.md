# planner design system

The tokens, brand book and components of the app. `tokens.json` holds the colors (light and dark), type, spacing, radius and shadow. Each folder in `components` has a `README.md` with the rules and a `preview.html` you can open in a browser (it expects the tokens as CSS variables).

planner is a task list that stays out of the way: a plain page, a list, one line to add things. The look is neutral and Notion-like, with a touch of console. Build every screen from the tokens and components here, and leave anything not described here out.

## Content fundamentals

- Write everything the app says in lowercase: `tasks`, `bin`, `add a task`, `undo`, `move to bin`. Never title case, never exclamation marks, never emoji.
- Say what happens, in the fewest words: `moved to bin`, `clear`, `empty bin`. The undo note is a pill, not a banner.
- Speak about things, not the system: `add inside: prep tfm`, not `parent task selected`.
- New tasks start lowercase because the keyboard does not capitalize. Never rewrite what a person typed.
- Numbers are the voice of state: the open count beside the title, `0/2` done of total beside a parent, the bin count beside the bin.

## Visual foundations

- Color: `page` is the ground, `ink` the text, `quiet` for anything secondary. `aim` marks the one thing that is targeted or being moved. The three priority colors are marks, never text or fills; `priority-high` doubles as the single accent (the remove side of a swipe).
- Themes: always design light and dark together. The priority colors are identical in both.
- Type: set screen titles and every small number or note in the mono families (`title`, `note`, `target`). Set everything a person reads or types in `body`. Quiet buttons use `label`.
- Spacing: text starts at `gutter`. Each level of subtask moves right by `indent`, and the priority dot moves with its task. There are four levels at most: main task, subtask, sub-subtask, sub-sub-subtask. Anything tappable is at least `touch` tall.
- Dots: every priority dot is `dot` across. Never size a dot to show importance; color is the only signal, and the order of the list is the second.
- Borders and shadows: there are none at rest. Separate things with space. The only shadow is `lift` on the row being dragged.
- Radius: the choosers use `radius-check`; the undo pill uses `radius-pill`. Nothing else is rounded: the check box is sharp.
- Motion: things move only when something happened. A dragged row follows the finger; rows slide to make room; a small haptic tick marks each change of drop spot. No decorative animation; respect the system's reduced motion setting.
- Layout: one column, full width. Two tabs run along the top, tasks at the left and agenda at the right, with the screen's own bar under them (counts at the left, controls at the right). The agenda has its own bin, and a quiet plus sits at its bottom right. The the add bar is pinned to the bottom above the keyboard, and the list scrolls between them. An invisible row of `end-row` ends the list.

## Behaviour that shapes the interface

- One order: each level of the list is ranked by priority. Priority only ranks a task among its siblings, so a subtask never leaves its parent.
- Touching a task opens or closes its subtasks. While the add line is in use, touching a task targets it instead; leaving the add line drops the target.
- Sweeping sideways across empty space switches tabs: left on tasks opens the agenda, right on the agenda goes back. It never loops, and it ignores any sweep that starts on a row.
- Swipe a row right to move it to the bin, left to rename it. Long press and drag to move a task, with the finger moving sideways to choose how deep the task lands.
- The agenda lists events soonest first with the time, name, location and notes all on view. A touch edits an event, a swipe right moves it to the event bin. An event has a name, a day, a time that is all day, a start, or a start and an end, a location and notes. The form is empty lines, the day and time choosers are drawn in the app's own style, and the add button is a quiet plus at the bottom right.
- There is no collapse arrow on a task; a parent shows `done/total` on the right. Every parent starts closed when the app opens. One small triangle at the top, left of the bin, opens everything (pointing down, shown while anything is closed) or closes everything (pointing up).
- Four levels at most.
- Holding the tasks tab opens a small menu with export and import, as wide as its two words, on `panel` with a `line` border and no shadow. They use the phone's file picker, and a backup carries the tasks and the agenda. Import only adds what is missing and never changes what is already there.

## Iconography

- The app icon is the four priority levels as a two by two grid of dots on the dark `page`: `priority-high`, `priority-medium`, `priority-low` and an outline ring.
- Icons are drawn with `stroke-thin` round-capped lines in `quiet`, `icon` across in a `touch` square: the bin (a can with a lid and handle). There is no icon font.
- The open count is drawn as seven-segment digits, like a calculator watch, in `quiet`, with the unlit bars faintly visible. Draw them, never set them in a font.
- Do not add decorative icons. If a control needs a symbol, prefer a word.

## Spacing and layout

Every number sits on a 4px grid (`space-1` to `space-6`) or is a named size (`touch`, `icon`, `check`, `dot`, and the rest of the spacing tokens). The app reads the same names from `Space.kt`, so nothing on a screen has a number of its own.

| Where | Rule |
| --- | --- |
| Left edge of text | `gutter` on every screen |
| Rows and bars, sides | `row-pad` each side; the control at the right fills the rest of its `touch` slot |
| Row height | `touch`, never less; a title with two lines grows by `space-3` above and below |
| Title bar | `space-2` above and below the title, `gutter` at the left, `row-pad` at the right |
| Title and its count | `space-3` apart; a second, fainter count follows `space-3` further |
| Priority dot | `dot` across in a `dot-lane` by `touch`; each level of depth adds `indent` before it |
| Check box | `check` across in a `touch` square, outlined with `stroke-thin` |
| Icons | `icon` across in a `touch` square, `stroke-thin` lines |
| Undo pill | `gutter` on the left, `row-pad` on the right, `space-1` above the add bar |
| Add bar | sits at the bottom edge; target line starts at `gutter` |
| End of the list | an invisible row of `end-row` |
| Drop line | `stroke-thick` tall, starting at `row-pad` plus one `indent` for each level of depth |
| Seven-segment digits | `digit-width` by `digit-height`, `digit-gap` apart, bars `digit-bar` thick |

Never invent an in-between number. If something does not fit a step, change the step for everyone.
