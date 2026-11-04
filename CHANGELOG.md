# changelog

what changed in each release, newest first. these notes are also the text of each [release](https://github.com/fourthbreakfast/planner/releases).

## 1.0.1

- windows: the three columns are panels on a darker ground, each with a header strip and a foot strip, and the add-a-task line is a bordered field in line with everything else
- nothing changes on the phone

## 1.0.0

- events can last several days and repeat by day, week, month or year, with a last day; deleting or changing one time of a repeating event asks whether it is this time or every time
- events from other calendars can be imported from an .ics file, in the settings on the phone and on windows. reading the same file again adds nothing
- devices are named on themselves and are called the same everywhere; there is a sync now button on the sync screen
- pairing works without broadcasts, so it also works on networks that block them, and the phone asks for the local network permission android 17 requires. the scanner opens upright
- windows: the window snaps like any other, by dragging the title bar to an edge, with the windows and arrow keys, and from the maximize button. the settings sit in the title bar, and the columns, add lines and icons are laid out on one grid
- sync is steadier: a clock that never goes back, equal times settle the same way on every device, a delete keeps its time when it travels, and a device asking to sync waits for a yes
- updates download in the background and are checked against the checksum github publishes
- reminders only ring for events still there, and late ones show when the phone wakes
- a restored backup stays restored, and phone backups stay out of the cloud
- a delete could come back between devices when one device's clock ran behind; deletes now keep the time they were made. a cancelled time of a repeating event in an imported calendar no longer shows, a later time of a long repeating event shows the right last day, and dragging one on the computer keeps its length
- pairing while sync was not running no longer crashes the phone, a failed background sync no longer leaves syncthing running, and a syncthing left behind by a killed app is stopped before the next one starts
- on the computer, removing a repeating event asks whether it is this time or every time, notes drag as on the phone and can be dropped last, the tray menu and the title bar are plainer, and Enter accepts a device name
- winget and obtainium are no longer used; the release is the apk and the msi

## 0.8.0

- both apps look for a newer release once a day and offer it in the settings; one touch downloads it and starts the installer. the daily look can be turned off
- planner for windows: tasks, agenda and notes side by side in one window, with keyboard shortcuts, right click menus and dragging. it stays in the tray when the window is closed, so reminders and sync go on, and syncs with the phone through the same built in syncthing. install it with the msi in the release
- the agenda can show a month: the grid icon at the top switches between the list and the month, days with events have a dot, and touching a day shows its events under the grid. a new event added from the month starts on the chosen day

## 0.7.0

- sync between your devices, built in: pair a device from settings by scanning or typing its code. it uses a private copy of syncthing, stays on your own network unless you turn on sync anywhere, and needs network access, which the app now asks for
- check boxes are smaller and sharp, and fill with a block once done
- tasks are always ranked by priority, and the dot that switched to the order they were put in is gone
- a task with subtasks starts closed each time the app opens
- a reminder can be dismissed from its notification, beside snooze
- touching just past the end of a line in a note puts the cursor there, not at the start of the next line

## 0.6.0

- a note is now one text: select across lines and paragraphs to delete, restyle or turn them into a list
- enter makes a plain new line, and two enters leave a gap for a new paragraph
- check boxes are drawn like the ones on tasks, in notes and in their previews
- a row only has to be swiped a quarter of the way to bin or rename it

## 0.5.2

- the search is only on the notes tab, beside the bin, and it searches only the notes. the tasks and agenda bars are back to how they were

## 0.5.1

- the search magnifier now sits right after the count on every tab, so the controls at the right edge stay as few as before, and it searches the tab you are on: tasks, events or notes. a task you touch in the results is scrolled to, with the closed tasks above it opened
- fixed: a white flash between screens in dark mode

## 0.5.0

- a search over tasks, events and notes, from the magnifying glass beside the bin
- a home screen widget with today's events and your open tasks, added from the settings
- hold the app icon for a new task, event or note; text shared from another app becomes a note
- a reminder notification opens its event when touched, and has a snooze 10 min button
- pin a note to keep it above the others
- screens slide the way you swept, and fade for settings, bins and forms (nothing moves if the phone has animations off)
- new settings: a light, dark or system theme, a daily backup written to a folder you choose (the last week is kept), and a link to the latest release
- the bins keep the tab row: the word bin sits where its tab sits, over only the count and the back arrow
- the body font is now ibm plex sans, with ibm plex mono for numbers and headings
- today follows the clock, so the agenda is right at midnight and when you come back to the app
- fixed: the task list opened scrolled down when it was long; notes left empty by a closed app showed as blank rows; the agenda's time column clipped "all day" and 12-hour times at large text sizes; empty search and note title fields now have a name for screen readers

## 0.4.0

- reminders for events with a start time: at the time, 10 minutes before or an hour before
- a settings screen, reached by holding any tab: export and import, where the notes toolbar sits, and a list of every gesture
- undo and redo in notes, and ctrl+z, b, i, u from a keyboard
- the first row slides open and shut once, so people find out rows move
- priority dots differ in size as well as colour, so they read without colour
- a darker quiet grey in light mode so small text is easier to read, and tabs dim while pressed

## 0.3.1

- in priority mode the line shown while dragging a task sits where the task will really land

## 0.3.0

- sweep sideways anywhere to switch tabs; to swipe or move a row you hold it for a moment first, so nothing is removed by accident
- every drop place now means one thing: the middle of a task puts it inside, a line puts it above the task under the line
- dragging works in priority mode too, snapping back among tasks of the same colour
- switching between your own order and priority order goes back to the top of the list

## 0.2.0

- an agenda: events with a day, an optional time (all day, a start, or a start and end), a place and notes, with a bin of its own
- a notes tab: titles, lists, checkboxes, bold, italic and underline, put in the order you choose
- our own clock and calendar for choosing times and days
- tasks, agenda and notes as tabs along the top, with the tab words and the counts drawn like a calculator watch
- backups carry events and notes, and older backup files still import
- the phone stays upright

## 0.1.0

- the first release: tasks with subtasks up to four levels deep, priorities, a bin with undo, and export and import
