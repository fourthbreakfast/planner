# planner design system

planner is a calm workspace for tasks, an agenda and notes. Readable text, slate surfaces and a teal navigation accent give each screen a predictable hierarchy. The app stays compact enough for daily use without making important controls hard to find.

The Kotlin theme in `android/shared/src/commonMain/kotlin/dev/fourthbreakfast/planner/ui/theme` defines the shipped appearance. `tokens.json` mirrors its colors and named sizes for reference previews. Phone screens use tabs and touch targets; Windows shows three columns with smaller mouse controls.

## Content

- Keep interface copy lowercase, brief and concrete: `tasks`, `add a task`, `moved to bin`, `undo`.
- Preserve the user's capitalization and wording.
- Name the action rather than the implementation: `add inside: trip`, `copy`, `restore`.
- Counts explain state: open tasks, direct-child progress, upcoming events and items in a bin.

## Color and hierarchy

- `page` is the slate-tinted ground; `panel` separates raised surfaces; `line` provides quiet dividers.
- `ink` carries readable content. `quiet` carries metadata and secondary controls, with at least 4.5:1 contrast on the page.
- Teal `accent` identifies selected navigation, focus and text selection. Selection also has a visible indicator, so color is not its only signal.
- `aim` identifies a targeted task, a drag destination or the rename side of a swipe.
- Priority uses red, amber and blue marks. High, medium and low have diameters of 10, 8 and 6dp; the no-priority ring is 8dp. Accessible labels name the level. Kotlin's `dotHighRadius`, `dotMediumRadius` and `dotLowRadius` are radii; JSON tokens are diameters.
- Red backgrounds identify destructive actions. White labels on them exceed 4.5:1 contrast.
- Design and inspect light and dark together.

## Typography

IBM Plex Sans is used for navigation, headings, body text and actions. Screen headings use the 22sp bold title style; smaller headings use the 16sp medium style. IBM Plex Mono gives times, counts and short metadata a steady rhythm. Counts use the same readable text system rather than a separate drawing.

Fonts are bundled. Desktop scales the shared typography to seven eighths of the phone scale. Respect the user's text size; event times have a separate width for a twelve-hour clock.

## Layout and controls

The shared spacing scale is 4, 8, 12, 16, 20 and 24dp, with named sizes for recurring roles. A specialized control can have a documented size of its own.

| Role | Rule |
| --- | --- |
| Phone navigation | Three equal tabs: tasks, agenda, notes; selected text and a short teal indicator |
| Phone controls | At least 48dp touch targets, even when drawings are small |
| Desktop controls | `Desk` scale: 32dp rows and mouse targets, 16dp drawings, 12dp inset |
| Text gutter | 16dp |
| Task indent | 20dp per level, four levels maximum |
| Priority marker | A 40dp touchable lane beside task text |
| Check box | A sharp 14dp square; checked is filled with a contrasting tick |
| Note marker | 26dp reserved before each marked line |
| Event time | 64dp for 24-hour time, 88dp for 12-hour time, scaled with text size |
| List ending | At least 72dp or a third of available height, allowing the last row to be brought into view |
| Desktop dashboard | Three independently scrolling columns; editors and settings open in a dismissible side panel |

Keep thin drawn icons for pin, bin, search, calendar and settings. Prefer a word when a symbol would be ambiguous. Avoid decorative icons and unnecessary shadows.

## Interaction

- A task opens or closes its children. While adding, touching a task chooses where the new task goes. Parents start closed.
- Siblings are ordered by completion and priority. Dragging changes placement within that order; a child stays under its parent.
- On the phone, hold before swiping or dragging a row. An ordinary horizontal sweep changes tabs, stops at the ends and respects the system back-gesture edge.
- A task's right swipe moves it to the bin with undo; its left swipe renames it. A note's left swipe copies its text.
- Dragging onto a task nests at the end; a line inserts above the next task at that depth. The drop indication must match the final priority-ranked placement.
- An event opens its editor. Repeating events ask whether a change concerns one occurrence or the series. Month mode marks busy days.
- Notes support lists, check boxes, indentation and emphasis, with undo, redo and ordered autosave. Pinned notes stay above unpinned notes.
- Bins allow restore and permanent deletion. Holding a bin asks once before emptying it.
- Settings open from the cog or a held phone tab. Back closes the current surface; Escape does the same on Windows.
- Motion explains a state change. Respect system reduced motion and avoid decorative animation.

## Maintaining this reference

Change shared theme roles before adding per-screen colors. Update `tokens.json`, component notes and previews when the shipped component changes. Reference HTML expects tokens as CSS variables. Shipped Kotlin behavior and rendered app checks take precedence over an old preview.
