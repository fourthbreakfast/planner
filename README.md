<p align="center">
  <img src="docs/readme/banner.svg" alt="planner" width="560">
</p>

<p align="center">
  <code>android</code> <code>kotlin</code> <code>offline</code> <code>todo</code> <code>agpl-3.0</code>
</p>

<p align="center">
  <img src="docs/readme/hero.png" alt="the task list in dark and light, with a task being swiped to the bin" width="100%">
</p>

planner is a task list, agenda and notebook for android. it is a quick standalone first try, separate from any larger planned apps, that turned out to be useful on its own. tasks can have subtasks up to four levels deep, each one can have a priority, and anything you remove goes to a bin first. the agenda lists your events soonest first, with their day, time, place and notes. notes are plain text you can mark up: lists, checkboxes, bold, italic and underline. everything is stored on your phone, so there's no account and it works offline.

### install

download the apk from the [latest release](https://github.com/fourthbreakfast/planner/releases/latest) and open it on your phone. you'll need android 8 or newer, and your phone will ask you to allow installing it.

### use

<table>
  <tr><td>touch a task</td><td>open or close its subtasks</td></tr>
  <tr><td>touch a task while typing</td><td>add the new task inside it</td></tr>
  <tr><td>touch the dot on a task</td><td>change its priority</td></tr>
  <tr><td>hold, then swipe right</td><td>move it to the bin</td></tr>
  <tr><td>hold, then swipe left</td><td>rename it</td></tr>
  <tr><td>hold and drag</td><td>move it: onto the middle of a task puts it inside at the end, onto a line puts it just above the task under the line. in priority order it snaps back among its own colour</td></tr>
  <tr><td>the dot at the top</td><td>switch between your own order (grey) and priority order (red)</td></tr>
  <tr><td>the triangle at the top</td><td>open or close everything</td></tr>
  <tr><td>tasks, agenda and notes along the top</td><td>touch a word to switch to that tab</td></tr>
  <tr><td>sweep sideways anywhere</td><td>left goes on to the next tab (tasks, agenda, notes), right goes back, and it never loops</td></tr>
  <tr><td>plus at the bottom right of the notes</td><td>add a note and start writing</td></tr>
  <tr><td>touch a note</td><td>write in it: the row at the top sets pull out, push in, numbered list, bulleted list, checkbox, bold, italic and underline; enter makes a new line and ends an empty list item</td></tr>
  <tr><td>hold a note and drag</td><td>put it in another place in the list</td></tr>
  <tr><td>hold a note, then swipe right</td><td>move it to the notes bin, with undo</td></tr>
  <tr><td>plus at the bottom right of the agenda</td><td>add an event: name, day, time (all day, a start, or a start and end), place and notes</td></tr>
  <tr><td>touch an event</td><td>edit it</td></tr>
  <tr><td>hold an event, then swipe right</td><td>move it to the agenda's own bin, with undo</td></tr>
  <tr><td>hold any tab</td><td>open the settings: export and import, where the notes toolbar sits, and a list of every gesture</td></tr>
  <tr><td>undo and redo at the top of a note</td><td>step back and forward through what you wrote and marked (ctrl+z and ctrl+b, i, u work with a keyboard)</td></tr>
</table>

### build

planner is written in kotlin with jetpack compose, and stores tasks with room. to build it, open the `android` folder in android studio and press run. the tool versions it was tested with are in [android/README.md](android/README.md), and the look is documented in the [design system](docs/design-system).

### credits

the body font is [inter](https://github.com/rsms/inter), under the SIL open font license ([docs/licenses/Inter-OFL.txt](docs/licenses/Inter-OFL.txt)).

### license

[agpl-3.0](LICENSE)
