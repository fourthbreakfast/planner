The shared checkbox is a 16dp square with 2dp corners and a 1.5dp outline. The unchecked state uses the essential control outline token, which stays stronger than structural dividers. The checked state fills with neutral ink and shows a fine contrasting tick. Tasks, notes and formatting controls reuse CheckBoxMark.

The interactive target remains 48dp on touch surfaces. A quiet hover/pressed surface and visible neutral keyboard-focus outline support pointer and keyboard use. Press feedback is restrained; completion also provides a haptic response. Each task checkbox exposes its task title and checkbox state to accessibility services.

Use neutral ink, never priority color. The filled square and tick communicate completion independently of color. Existing completion behavior remains: completing a task completes descendants; reopening a task reopens ancestors.
