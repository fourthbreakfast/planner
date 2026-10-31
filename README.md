<p align="center">
  <img src="docs/readme/banner.svg" alt="planner" width="560">
</p>

<p align="center">
  <code>android</code> <code>kotlin</code> <code>offline</code> <code>todo</code> <code>agpl-3.0</code>
</p>

<p align="center">
  <img src="docs/readme/hero.png" alt="the task list in dark and light, with a task being swiped to the bin" width="100%">
</p>

planner is a task list for android. tasks can have subtasks up to four levels deep, each one can have a priority, and anything you remove goes to a bin first. everything is stored on your phone, so there's no account and it works offline.

### install

download the apk from the [latest release](https://github.com/fourthbreakfast/planner/releases/latest) and open it on your phone. you'll need android 8 or newer, and your phone will ask you to allow installing it.

### use

| | |
| --- | --- |
| touch a task | open or close its subtasks |
| touch a task while typing | add the new task inside it |
| touch the dot on a task | change its priority |
| swipe right | move it to the bin |
| swipe left | rename it |
| hold and drag | move it, sideways to change how deep it goes |
| the dot at the top | switch between your own order (grey) and priority order (red) |
| the triangle at the top | open or close everything |
| hold the title | export your tasks to a file, or import them back |

### build

planner is written in kotlin with jetpack compose, and stores tasks with room. to build it, open the `android` folder in android studio and press run. the tool versions it was tested with are in [android/README.md](android/README.md), and the look is documented in the [design system](docs/design-system).

### license

[agpl-3.0](LICENSE)
