# Changelog: CRS Scheduler v2.0

## New

- **Profile section** (config screen, collapsed by default) — optional name, student number, course, and year/standing, each with its own switch for whether it appears on the exported schedule image. Off by default.
- **Form 5 section** (config screen, collapsed by default) — attach a Form 5 PDF for quick access (open, replace, or remove) later. Targeted for freshmen with no physical IDs yet.
- **First-run setup wizard** — the very first time you open the config screen with nothing loaded, it walks you through three steps (Import, Review your classes, then Profile & Extras) instead of showing every card at once.
- **Save and Form 5 buttons on both widgets** (Today and Week, all sizes) — Save opens a chooser for Image or `.ics`; Form 5 opens your attached PDF directly or prompts you to attach one.
- **Exported image footer** — the app credit line is now smaller and more subtle, and whichever profile fields are switched on now appear as a line near the class/unit count.
- **Three app themes** — pick from a card in settings, no reinstall needed: GE (UP-maroon, follows system light/dark), NE (fixed dark, red-on-black), or Adaptive (matches your wallpaper, Android 12+). Applies everywhere: widgets, full schedule screen, and exported images.
- **Add a class manually** — for anything CRS doesn't cover (lab sessions, study groups, etc.), add one by hand with name, days, start/end time, and optional room/instructor/units.
- **Delete a class** — remove one outright with a confirmation first; it's saved to Saved Schedules first so it's recoverable.
- **New progress ring style** — GE and Adaptive get a smooth stroked arc; NE keeps the original 28-dot glyph ring.

## Widget header rework

Getting Save and Form 5 icons onto every widget size meant reworking tight header layouts. On the compact Today widget and both Week widget sizes, "VIEW FULL" became a small arrow icon instead of a text chip, and the TODAY/WEEKLY label now shrinks first under space pressure so the icons and clock never overlap. The full-size Today widget didn't need these changes — the new buttons just joined the existing row.

## Fixed

- **Countdown no longer goes negative** — the widget now catches the exact moment a class ends or starts and refreshes automatically, instead of relying on a 15-minute check-in.
- **Widgets recover properly after a phone restart** — the refresh alarms are restored automatically, so placed widgets don't go quiet.
- **Class list no longer gets cropped or dimmed near the bottom** of the Today widget on some sizes.
- **Weekly grid always shows your full week** — instead of hiding extra slots behind a "+N more" row, it shrinks everything slightly so the whole week is visible.
- **Fixed occasional squished rows** in the Today widget's class list when the list recycled rows.
- **Exported schedule images now match your chosen theme** (colors and font) instead of always coming out in the old dark/mono look.
