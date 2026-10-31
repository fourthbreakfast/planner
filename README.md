<p align="center">
  <img src="docs/readme/banner.svg" alt="planner" width="560">
</p>

<p align="center">
  <code>android</code> <code>kotlin</code> <code>offline</code> <code>todo</code> <code>agpl-3.0</code>
</p>

<p align="center">
  <img src="docs/readme/hero.png" alt="the task list in dark and light, with a task being swiped to the bin" width="100%">
</p>

planner is a task list and agenda for android. tasks can have subtasks up to four levels deep, each one can have a priority, and anything you remove goes to a bin first. the agenda lists your events soonest first, with their day, time, place and notes. everything is stored on your phone, so there's no account and it works offline.

### install

download the apk from the [latest release](https://github.com/fourthbreakfast/planner/releases/latest) and open it on your phone. you'll need android 8 or newer, and your phone will ask you to allow installing it.

### use

<table>
  <tr><td>touch a task</td><td>open or close its subtasks</td></tr>
  <tr><td>touch a task while typing</td><td>add the new task inside it</td></tr>
  <tr><td>touch the dot on a task</td><td>change its priority</td></tr>
  <tr><td>swipe right</td><td>move it to the bin</td></tr>
  <tr><td>swipe left</td><td>rename it</td></tr>
  <tr><td>hold and drag</td><td>move it, sideways to change how deep it goes</td></tr>
  <tr><td>the dot at the top</td><td>switch between your own order (grey) and priority order (red)</td></tr>
  <tr><td>the triangle at the top</td><td>open or close everything</td></tr>
  <tr><td>the word agenda at the top</td><td>switch to your events, and tasks switches back</td></tr>
  <tr><td>plus in the agenda</td><td>add an event: name, day, time, place and notes</td></tr>
  <tr><td>touch an event</td><td>edit it</td></tr>
  <tr><td>swipe an event right</td><td>delete it, with undo</td></tr>
  <tr><td>hold the title</td><td>export everything to a file, or import it back</td></tr>
</table>

### build

planner is written in kotlin with jetpack compose, and stores tasks with room. to build it, open the `android` folder in android studio and press run. the tool versions it was tested with are in [android/README.md](android/README.md), and the look is documented in the [design system](docs/design-system).

### license

[agpl-3.0](LICENSE)
