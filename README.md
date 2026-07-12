# CRS Scheduler

A Nothing OS-inspired Android home screen widget pair for your UP CRS class
schedule, ported from an HTML/JS prototype into a real Android Studio project.

**Author:** [marquinhhou](https://github.com/marquinhhou)
**Credits:** Marc Dizon, BS Statistics -- original concept and web prototype
(`nothing_schedule.html`) that this app is ported and extended from.

This is an independent, unofficial student project. It is **not affiliated
with, endorsed by, or connected to** the University of the Philippines
Diliman, the UP School of Statistics, the UP Computerized Registration
System (CRS), or Nothing Technology Limited. "Nothing OS" is referenced 
solely to describe the application's design inspiration.

---

## What's in the box

1. **Today** -- a hero card for your current/next class (with a countdown
   ring), plus a list of the rest of today's classes.
2. **This Week** -- a compact summary (class count, units, next upcoming
   class, a week-at-a-glance dot row) that opens the full schedule when
   tapped.

The **entire week schedule** lives as a real in-app screen
(`WeekScheduleActivity`), not crammed into a widget -- it's fully scrollable
and every class is tappable, unlike a RemoteViews-hosted list.

Tapping any class (in either widget, or in the full-schedule screen) offers to
open your phone's maps app for that room, if a room is set.

## Features

- **CRS HTML parsing** -- paste the saved page source (or pick a saved
  `.html` file) of your CRS Schedule page in the config screen; the app finds
  the "Enlisted Classes" table and extracts code, name, credits, days,
  times, type, room, and instructor. This is a Java/Jsoup port of the
  original web widget's parser, so both stay in sync on what counts as a
  valid row.
- **.ics import** -- the same "choose a file" picker also accepts a
  `.ics` calendar file (one exported from here previously, or from another
  calendar/university system) as an alternate way to load a schedule,
  no HTML page needed. Since a calendar file has no concept of academic
  credits, imported classes come in at 0 units, flagged as excluded from
  the unit total rather than silently faking a number -- correct them
  under Edit Class Info if you want them counted.
- **Edit Class Info** -- toggle it on in the config screen to see every
  loaded class, tap one, and manually set/correct its room, instructor,
  or unit count at any time (not just for TBA rows or blank fields).
- **Semester dates** -- optionally set when the schedule actually starts
  and ends. Outside that range the widgets and full schedule show a
  "not in session" state instead of treating every day as a normal school
  day; once the end date passes, the schedule is auto-archived to Saved
  Schedules and cleared, same as a manual Clear would do.
- **Saved Schedules** -- every schedule you replace (by loading a new one,
  clearing, or letting a semester end) is auto-archived with a
  "date · N classes · X.X units" label. Browse, reactivate, or delete
  past schedules from the config screen at any time.
- **Class reminders** -- optionally get a local notification 5/10/15/30
  minutes before each class starts. Alarms are rescheduled automatically
  after a reboot, and the permission prompts (notifications, exact alarms)
  only ever show up once you actually turn reminders on.
- **Widget preview & tomorrow toggles** -- before a semester starts, tapping
  the widget header lets you preview what it'll look like once classes
  begin. The Today widget also has a TMRW chip to peek at tomorrow's
  classes instead of today's.
- **Export your schedule** -- from the full schedule screen, save it as an
  image to your gallery, or export it as a standard `.ics` calendar file to
  import into Google Calendar, Outlook, or any other calendar app.
- **Maps hand-off** -- tapping a class with a room set asks "Open Maps for
  X?" and, if you say yes, launches a `geo:` search intent so whichever maps
  app you have installed can handle it. An optional "Campus / school name"
  field (config screen) gets appended to the search to help disambiguate
  (e.g. "SS 301 UP Diliman" instead of just "SS 301").
- **First-run Terms of Use** -- shown once before the widget can be added;
  covers where the data comes from (CRS page or .ics import), that
  room/instructor/unit edits aren't verified by the app, that class
  reminders are scheduled entirely on-device, that everything stays
  on-device, and that there's no warranty.
- **Everything stays on-device.** All schedule data and settings are stored
  in local `SharedPreferences` only. Nothing is ever uploaded anywhere --
  there is no network permission in this app at all. Exporting a schedule
  (image or `.ics`) just writes a file locally for you to share however
  you choose.

## Project structure

```
app/src/main/java/dev/marquinhhou/crsscheduler/
  model/     ClassSession.java          -- the parsed-class data model
             ScheduleSnapshot.java      -- an archived schedule + its auto-generated label
  data/      ScheduleParser.java        -- Jsoup-based CRS table parser
             IcsImporter.java           -- parses a .ics file back into ClassSessions
             ScheduleStore.java         -- SharedPreferences persistence (active schedule)
             ScheduleHistoryStore.java  -- SharedPreferences persistence (archived schedules)
             SemesterArchiver.java      -- auto-archives the schedule once semesterEnd passes
             SettingsStore.java         -- campus hint, semester dates, reminders, terms-accepted flag
  reminders/ ClassReminderScheduler.java-- schedules/cancels the per-class AlarmManager alarms
             ClassReminderReceiver.java -- fires the actual notification when an alarm goes off
             NotificationHelper.java    -- notification channel + builder
             BootReceiver.java          -- re-schedules reminders after a device reboot
  widget/    TodayWidgetProvider.java   -- tier 1 widget
             WeekWidgetProvider.java    -- tier 2 widget (summary + entry point)
             WidgetRenderer.java        -- shared RemoteViews builder for both
             WidgetRefreshScheduler.java-- battery-friendly 15-min refresh alarm
             RingBitmapFactory.java     -- draws the countdown "dot ring" glyph
             TodayClassesRemoteViewsService.java -- ListView adapter for the Today widget
  ui/        ConfigureActivity.java     -- import/parse/save, Edit Class Info, Terms gate
             WeekScheduleActivity.java  -- the full in-app schedule screen
             ScheduleHistoryActivity.java -- browse/reactivate/delete Saved Schedules
             IcsExporter.java           -- builds a .ics file from the current schedule
             ScheduleImageExporter.java -- renders the schedule to a bitmap and saves it to the gallery
             WidgetActionActivity.java  -- dialog-themed "open maps?" popup
```

## Setting it up in Android Studio

1. Open the project folder in Android Studio and let it sync (it'll fetch
   the Gradle wrapper jar itself on first sync if it isn't already cached).
2. Build & run once to install the app -- there's no launcher icon by
   design (it's a widget-only app), so you won't see it in the app drawer.
   That's expected.
3. Long-press your home screen -> Widgets -> **CRS Scheduler** -> drag the
   **Today** widget onto your home screen. This triggers the required setup
   screen (Terms of Use, then paste/load your CRS schedule).
4. Optionally add the **This Week** widget too, and drag it onto the Today
   widget if your launcher supports stacking widgets together.

### Getting your CRS schedule into the app

1. Open your CRS Schedule page (the one with the Enlisted Classes table) in
   a desktop browser.
2. Save it (`Ctrl+S` -> "Webpage, HTML only") or view source (`Ctrl+U`,
   select all, copy).
3. In the widget's setup screen, either pick the saved `.html` file or paste
   the copied source into the box, then tap **Parse & Save**.
4. Whenever your enlistment changes, just repeat this -- the app doesn't
   talk to CRS directly and won't know about changes on its own.

Already have a `.ics` calendar file instead -- e.g. one exported from this
app previously, or from another calendar/university system? Pick it with the
same file button; it's detected and imported automatically, no HTML needed.
Since a calendar file doesn't carry a credit count, imported classes come in
at 0 units (excluded from the unit total) -- fix that under Edit Class Info
if you want them counted.

## Known limitations / design notes

- **Refresh granularity:** both widgets refresh every 15 minutes via
  `AlarmManager.setInexactRepeating()`, not every minute. This needs no
  special permission and respects Doze/App Standby, at the cost of the
  countdown/ring being up to ~15 min stale between taps -- the same
  granularity most weather/calendar widgets use. An exact-alarm-per-minute
  approach was deliberately avoided as overkill and battery-unfriendly for
  this use case.
- **No online room lookup.** An earlier draft of this feature tried to
  web-search for TBA rooms automatically, but that would've required every
  user to register their own Google Custom Search API key just to use the
  app -- too much friction for what it's worth. Manually editing a class's
  room via **Edit Class Info** covers the same need without the setup
  burden.
- **Maps accuracy isn't guaranteed,** especially for manually-typed rooms --
  it's a plain text search handed to whatever maps app you have, not a
  verified campus room directory.

## License

MIT -- see [LICENSE](LICENSE). Third-party assets keep their own original
open-source licenses:

- Bundled fonts (JetBrains Mono, DotGothic16) -- SIL OFL 1.1, see
  [licenses/fonts](licenses/fonts).
- A handful of toolbar/dialog icon drawables redrawn from Google Material
  Icons -- Apache License 2.0, see [licenses/icons](licenses/icons).
