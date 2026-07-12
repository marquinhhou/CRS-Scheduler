package dev.marquinhhou.crsscheduler.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.ui.ConfigureActivity;
import dev.marquinhhou.crsscheduler.ui.WeekScheduleActivity;
import dev.marquinhhou.crsscheduler.ui.WidgetActionActivity;

/**
 * Builds the RemoteViews for both widgets. Rows are plain children added via
 * addView() (not a RemoteViewsFactory-backed collection), so each one just gets
 * its own direct PendingIntent -- no shared PendingIntentTemplate/fill-in-intent
 * dance is needed, which also sidesteps StackView's "no nested scrolling, patchy
 * launcher support" issues entirely.
 *
 * The clock is a plain android.widget.TextClock in each layout's XML rather than
 * something we push text into -- TextClock (like AnalogClock) is one of the view
 * types RemoteViews explicitly supports specifically because it knows how to keep
 * itself ticking once hosted in the launcher's process, with no alarms, wake-ups,
 * or per-minute rebuilds required from this app at all.
 *
 * SIZING: home screen widgets can be freely resized by the user, but the
 * reserved grid footprint that resize produces is controlled entirely by the
 * launcher (its own grid snapping) -- this app can never shrink that footprint
 * back down after the fact, only render less/more content within whatever it's
 * given. The Today widget's class list is a genuinely scrollable ListView (see
 * TodayClassesRemoteViewsService) backed by an AT_MOST-constrained parent, so it
 * naturally shows as many rows as fit and lets the rest be scrolled to -- no
 * manual row-counting needed there. The Week widget's grid still uses the
 * addView()-built static-row approach (a 2D table doesn't map onto a 1D
 * ListView as cleanly, since the day-letter header needs to stay pinned while
 * only the data rows scroll), so it estimates how many rows fit from the
 * content about to be rendered and truncates with a "+N more" row rather than
 * clipping silently. The callers (the AppWidgetProviders + WidgetRefreshScheduler)
 * re-fetch options for every widget id every time they rebuild it, since each
 * placed instance can be resized independently.
 */
public final class WidgetRenderer {

    public static final String[] DAY_LABELS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    public static final int[] WEEK_ORDER = {1, 2, 3, 4, 5, 6, 0}; // Mon..Sun

    private static final int RED = Color.parseColor("#FF2E17");
    private static final int INK = Color.parseColor("#F3F2ED");
    private static final int INK_DIM = Color.parseColor("#8B8B86");
    private static final int BG = Color.parseColor("#000000");

    private static final int TODAY_BASE_HEIGHT_DP = 190;  // padding + header + hero card + list title
    private static final int TODAY_ROW_HEIGHT_DP = 56;    // one row_class row, incl. its own bottom padding gap
    private static final int WEEK_BASE_HEIGHT_DP = 132;   // padding + header + headline + table padding + day-letter row + no-class-days note
    private static final int WEEK_ROW_HEIGHT_DP = 19;      // one grid data row

    private WidgetRenderer() {}

    // Today widget

    public static RemoteViews buildToday(Context context, Bundle options, int appWidgetId) {
        List<ClassSession> schedule = ScheduleStore.load(context);
        SettingsStore.SemesterPhase rawPhase = SettingsStore.currentSemesterPhase(context);
        SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(context);
        int today = calendarDayToJs(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        List<ClassSession> todays = new ArrayList<>();
        if (phase != SettingsStore.SemesterPhase.UPCOMING && phase != SettingsStore.SemesterPhase.ENDED) {
            for (ClassSession c : schedule) if (c.days.contains(today)) todays.add(c);
            todays.sort((a, b) -> Integer.compare(a.start, b.start));
        }

        int availableDp = currentPortraitHeightDp(options);
        boolean roomForList = availableDp == Integer.MAX_VALUE || availableDp >= TODAY_BASE_HEIGHT_DP;

        return roomForList
                ? buildTodayFull(context, schedule, todays, appWidgetId, availableDp, phase, rawPhase)
                : buildTodayCompact(context, schedule, todays, phase, rawPhase);
    }

    private static RemoteViews buildTodayFull(Context context, List<ClassSession> schedule,
                                               List<ClassSession> todays, int appWidgetId, int availableDp,
                                               SettingsStore.SemesterPhase phase, SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.today_widget);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
        bindHeaderBrandLabel(context, rv, TodayWidgetProvider.class,
                TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, "TODAY", rawPhase, 5);

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        ClassSession ongoing = null;
        for (ClassSession c : todays) {
            if (nowMin >= c.start && nowMin < c.end) { ongoing = c; break; }
        }
        ClassSession next = null;
        if (ongoing == null) {
            for (ClassSession c : todays) if (c.start > nowMin) { next = c; break; }
        }

        boolean outOfSession = phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED;
        if (ongoing != null) {
            float progress = (nowMin - ongoing.start) / (float) (ongoing.end - ongoing.start);
            showHero(rv, "NOW", ongoing, progress, ongoing.end, "%s left", true, true);
        } else if (next != null) {
            int gapWindow = 240;
            float progress = 1f - Math.min(1f, (next.start - nowMin) / (float) gapWindow);
            showHero(rv, "NEXT", next, progress, next.start, "in %s", false, true);
        } else if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            // Only reached when NOT already previewing (previewing folds phase
            // to ACTIVE upstream) -- so this is always the "off" state, safe
            // to offer turning preview on here.
            showEmptyHero(rv, "Semester hasn't started yet.",
                    "Classes begin " + formatSemesterDate(SettingsStore.getSemesterStart(context)) + ". Tap to preview anyway.", true);
            bindToggleTap(context, rv, R.id.hero_empty_sub, TodayWidgetProvider.class,
                    TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, 6);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            showEmptyHero(rv, "Semester has ended.",
                    "Classes ran through " + formatSemesterDate(SettingsStore.getSemesterEnd(context)) + ".", true);
        } else if (!schedule.isEmpty()) {
            showEmptyHero(rv, "Day's clear from here.", "Nothing left on today's schedule.", true);
        } else {
            showEmptyHero(rv, "No schedule loaded.", "Tap the gear to load your CRS classes.", true);
        }

        boolean showTomorrow = SettingsStore.isShowTomorrowEnabled(context);
        rv.setTextViewText(R.id.today_list_title,
                (!outOfSession && !schedule.isEmpty())
                        ? (showTomorrow ? "TOMORROW'S CLASSES" : "TODAY'S CLASSES") : "");
        rv.setTextViewText(R.id.today_list_empty, showTomorrow ? "No classes tomorrow." : "No classes today.");
        bindTomorrowToggle(context, rv, showTomorrow);

        bindTodayListAdapter(context, rv, appWidgetId, availableDp);

        return rv;
    }

    /** Header + hero card only -- used when there's no room for even a single class row. */
    private static RemoteViews buildTodayCompact(Context context, List<ClassSession> schedule,
                                                  List<ClassSession> todays, SettingsStore.SemesterPhase phase,
                                                  SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.today_widget_compact);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
        bindHeaderBrandLabel(context, rv, TodayWidgetProvider.class,
                TodayWidgetProvider.ACTION_TOGGLE_PREVIEW, "TODAY", rawPhase, 5);

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        ClassSession ongoing = null;
        for (ClassSession c : todays) {
            if (nowMin >= c.start && nowMin < c.end) { ongoing = c; break; }
        }
        ClassSession next = null;
        if (ongoing == null) {
            for (ClassSession c : todays) if (c.start > nowMin) { next = c; break; }
        }

        if (ongoing != null) {
            float progress = (nowMin - ongoing.start) / (float) (ongoing.end - ongoing.start);
            showHero(rv, "NOW", ongoing, progress, 0, "", true, false);
        } else if (next != null) {
            int gapWindow = 240;
            float progress = 1f - Math.min(1f, (next.start - nowMin) / (float) gapWindow);
            showHero(rv, "NEXT", next, progress, 0, "", false, false);
        } else if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            showEmptyHero(rv, "Semester hasn't started yet.", "", false);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            showEmptyHero(rv, "Semester has ended.", "", false);
        } else if (!schedule.isEmpty()) {
            showEmptyHero(rv, "Day's clear from here.", "", false);
        } else {
            showEmptyHero(rv, "No schedule loaded.", "", false);
        }

        return rv;
    }

    /**
     * countdownTargetMinute/countdownFormat are only used when includeCountdown is true.
     * The countdown is a Chronometer, not a static string: it counts down to the real
     * wall-clock target (class end for "NOW", class start for "NEXT") and keeps ticking
     * every second in the launcher process itself, so it's never stale even if the
     * widget's own RemoteViews haven't been rebuilt in a while -- see the class-level
     * comment on staleness for why that matters.
     */
    private static void showHero(RemoteViews rv, String label, ClassSession c, float progress,
                                  int countdownTargetMinute, String countdownFormat,
                                  boolean isNow, boolean includeCountdown) {
        rv.setViewVisibility(R.id.hero_content, View.VISIBLE);
        rv.setViewVisibility(R.id.hero_empty, View.GONE);
        rv.setImageViewBitmap(R.id.hero_ring, RingBitmapFactory.build(progress, 180, RED));
        rv.setTextViewText(R.id.hero_label, label);
        rv.setTextViewText(R.id.hero_class, c.name);
        rv.setTextViewText(R.id.hero_meta, c.displayRoom() + " \u00B7 " + minToLabel(c.start) + "\u2013" + minToLabel(c.end));
        if (includeCountdown) {
            long targetEpochMillis = todayMinuteToEpochMillis(countdownTargetMinute);
            long base = SystemClock.elapsedRealtime() + (targetEpochMillis - System.currentTimeMillis());
            rv.setChronometer(R.id.hero_countdown, base, countdownFormat, true);
            rv.setChronometerCountDown(R.id.hero_countdown, true);
        }
        rv.setInt(R.id.hero_card, "setBackgroundResource", isNow ? R.drawable.card_bg_now : R.drawable.card_bg);
    }

    /** Epoch millis for today's date at the given minute-of-day, for seeding a countdown Chronometer. */
    private static long todayMinuteToEpochMillis(int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static void showEmptyHero(RemoteViews rv, String title, String sub, boolean includeSub) {
        rv.setViewVisibility(R.id.hero_content, View.GONE);
        rv.setViewVisibility(R.id.hero_empty, View.VISIBLE);
        rv.setTextViewText(R.id.hero_empty_title, title);
        if (includeSub) rv.setTextViewText(R.id.hero_empty_sub, sub);
        rv.setInt(R.id.hero_card, "setBackgroundResource", R.drawable.card_bg);
    }

    // Week widget

    public static RemoteViews buildWeekSummary(Context context, Bundle options) {
        List<ClassSession> schedule = ScheduleStore.load(context);
        SettingsStore.SemesterPhase rawPhase = SettingsStore.currentSemesterPhase(context);
        SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(context);
        if (phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED) {
            return buildWeekCompact(context, schedule, phase, rawPhase);
        }

        List<Integer> breakpoints = schedule.isEmpty() ? new ArrayList<>() : weekBreakpoints(schedule);
        List<Integer> activeDays = schedule.isEmpty() ? new ArrayList<>() : activeWeekDays(schedule);
        List<Integer> occupiedRows = schedule.isEmpty()
                ? new ArrayList<>() : occupiedRowIndices(schedule, breakpoints, activeDays);
        int totalGridRows = occupiedRows.size();

        int availableDp = currentPortraitHeightDp(options);
        int maxRowsThatFit = (availableDp == Integer.MAX_VALUE)
                ? totalGridRows
                : Math.max(0, (availableDp - WEEK_BASE_HEIGHT_DP) / WEEK_ROW_HEIGHT_DP);

        if (schedule.isEmpty() || maxRowsThatFit <= 0) {
            return buildWeekCompact(context, schedule, phase, rawPhase);
        }
        int rowsToShow = Math.min(totalGridRows, maxRowsThatFit);
        return buildWeekExpanded(context, schedule, breakpoints, activeDays, occupiedRows, rowsToShow, rawPhase);
    }

    /** Compact "headline + next class + week dots" summary -- used when the grid won't fit, or the semester isn't in session. */
    private static RemoteViews buildWeekCompact(Context context, List<ClassSession> schedule,
                                                 SettingsStore.SemesterPhase phase, SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.week_widget);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
        bindHeaderBrandLabel(context, rv, WeekWidgetProvider.class,
                WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, "WEEKLY", rawPhase, 5);

        Calendar now = Calendar.getInstance();
        int today = calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (phase == SettingsStore.SemesterPhase.UPCOMING) {
            // Only reached when NOT already previewing (previewing folds phase
            // to ACTIVE upstream) -- so this is always the "off" state, safe
            // to offer turning preview on here.
            rv.setTextViewText(R.id.summary_headline, "Semester hasn't started yet.");
            rv.setTextViewText(R.id.summary_sub,
                    "Classes begin " + formatSemesterDate(SettingsStore.getSemesterStart(context)) + ". Tap to preview anyway.");
            rv.setTextViewText(R.id.summary_next, "");
            bindToggleTap(context, rv, R.id.summary_sub, WeekWidgetProvider.class,
                    WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, 6);
        } else if (phase == SettingsStore.SemesterPhase.ENDED) {
            rv.setTextViewText(R.id.summary_headline, "Semester has ended.");
            rv.setTextViewText(R.id.summary_sub,
                    "Classes ran through " + formatSemesterDate(SettingsStore.getSemesterEnd(context)) + ".");
            rv.setTextViewText(R.id.summary_next, "");
        } else if (schedule.isEmpty()) {
            rv.setTextViewText(R.id.summary_headline, "No schedule loaded");
            rv.setTextViewText(R.id.summary_sub, "Tap the gear to load your CRS classes.");
            rv.setTextViewText(R.id.summary_next, "");
        } else {
            double totalUnits = 0;
            int classCount = 0;
            for (ClassSession c : schedule) {
                if (!c.creditsExcluded) totalUnits += c.credits;
                classCount++;
            }
            rv.setTextViewText(R.id.summary_headline,
                    classCount + (classCount == 1 ? " class" : " classes")
                            + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units");

            ClassSession upcoming = null;
            int upcomingDay = -1;
            outer:
            for (int offset = 0; offset < 7; offset++) {
                int dayIdx = (today + offset) % 7;
                List<ClassSession> dayItems = new ArrayList<>();
                for (ClassSession c : schedule) if (c.days.contains(dayIdx)) dayItems.add(c);
                dayItems.sort((a, b) -> Integer.compare(a.start, b.start));
                for (ClassSession c : dayItems) {
                    if (offset == 0 && c.start <= nowMin && nowMin < c.end) {
                        upcoming = c; upcomingDay = dayIdx; break outer;
                    }
                    if (offset > 0 || c.start > nowMin) {
                        upcoming = c; upcomingDay = dayIdx; break outer;
                    }
                }
            }
            if (upcoming != null) {
                String when = upcomingDay == today ? "Today" : DAY_LABELS[upcomingDay];
                rv.setTextViewText(R.id.summary_sub, "Next: " + upcoming.name);
                rv.setTextViewText(R.id.summary_next,
                        when + " \u00B7 " + minToLabel(upcoming.start) + " \u00B7 " + upcoming.displayRoom());
            } else {
                rv.setTextViewText(R.id.summary_sub, "Nothing else scheduled this week.");
                rv.setTextViewText(R.id.summary_next, "");
            }
        }

        rv.removeAllViews(R.id.week_dots_row);
        for (int dayIdx : WEEK_ORDER) {
            boolean hasClasses = false;
            for (ClassSession c : schedule) if (c.days.contains(dayIdx)) { hasClasses = true; break; }
            RemoteViews dot = new RemoteViews(context.getPackageName(), R.layout.row_week_dot);
            dot.setTextViewText(R.id.week_dot_label, DAY_LABELS[dayIdx].substring(0, 1));
            dot.setInt(R.id.week_dot_label, "setTextColor", dayIdx == today ? INK : INK_DIM);
            if (dayIdx == today) {
                dot.setImageViewResource(R.id.week_dot, R.drawable.dot_red);
            } else {
                dot.setImageViewResource(R.id.week_dot, hasClasses ? R.drawable.dot_has_class : R.drawable.dot_dim);
            }
            rv.addView(R.id.week_dots_row, dot);
        }

        return rv;
    }

    /** CRS-style time/day grid, showing as many rows as fit the current height. */
    private static RemoteViews buildWeekExpanded(Context context, List<ClassSession> schedule,
                                                  List<Integer> breakpoints, List<Integer> activeDays,
                                                  List<Integer> occupiedRows, int rowsToShow,
                                                  SettingsStore.SemesterPhase rawPhase) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.week_widget_expanded);
        bindGear(context, rv);
        bindViewFullSchedule(context, rv);
        bindHeaderBrandLabel(context, rv, WeekWidgetProvider.class,
                WeekWidgetProvider.ACTION_TOGGLE_PREVIEW, "WEEKLY", rawPhase, 5);

        double totalUnits = 0;
        for (ClassSession c : schedule) if (!c.creditsExcluded) totalUnits += c.credits;
        rv.setTextViewText(R.id.table_headline,
                schedule.size() + (schedule.size() == 1 ? " class" : " classes")
                        + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units");

        buildWeekTable(context, rv, schedule, breakpoints, activeDays, occupiedRows, rowsToShow);

        String noClassDays = noClassDaysLabel(activeDays);
        if (noClassDays.isEmpty()) {
            rv.setViewVisibility(R.id.no_class_days_note, View.GONE);
        } else {
            rv.setViewVisibility(R.id.no_class_days_note, View.VISIBLE);
            rv.setTextViewText(R.id.no_class_days_note, noClassDays);
        }

        return rv;
    }

    private static void buildWeekTable(Context context, RemoteViews rv, List<ClassSession> schedule,
                                        List<Integer> breakpoints, List<Integer> activeDays,
                                        List<Integer> occupiedRows, int rowsToShow) {
        rv.removeAllViews(R.id.week_table_container);

        Calendar now = Calendar.getInstance();
        int today = calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));

        // header row: blank corner cell + one day-letter cell per day that
        // actually has a class this week. Days with nothing scheduled at all
        // (weekends, for most students) are dropped entirely rather than
        // rendered as a column of dots all the way down -- that column was
        // pure visual noise and its width is better spent on the days that
        // matter.
        RemoteViews headerRow = new RemoteViews(context.getPackageName(), R.layout.row_week_table);
        RemoteViews corner = new RemoteViews(context.getPackageName(), R.layout.cell_week_table_time);
        corner.setTextViewText(R.id.cell_time_text, "");
        headerRow.addView(R.id.table_row, corner);
        for (int dayIdx : activeDays) {
            RemoteViews dayCell = new RemoteViews(context.getPackageName(), R.layout.cell_week_table_daylabel);
            dayCell.setTextViewText(R.id.cell_daylabel_text, DAY_LABELS[dayIdx].substring(0, 1));
            dayCell.setInt(R.id.cell_daylabel_text, "setTextColor", dayIdx == today ? INK : INK_DIM);
            headerRow.addView(R.id.table_row, dayCell);
        }
        rv.addView(R.id.week_table_container, headerRow);

        // one data row per breakpoint slot that has a class on at least one
        // active day -- slots that are empty everywhere (a natural gap
        // between classes, e.g. a half hour nobody has anything) are skipped
        // rather than rendered as a row of dots that says nothing. Capped at
        // however many rows actually fit (see buildWeekSummary()).
        int shown = Math.min(rowsToShow, occupiedRows.size());
        for (int i = 0; i < shown; i++) {
            int r = occupiedRows.get(i);
            int slotStart = breakpoints.get(r);
            int slotEnd = breakpoints.get(r + 1);

            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row_week_table);
            RemoteViews timeCell = new RemoteViews(context.getPackageName(), R.layout.cell_week_table_time);
            timeCell.setTextViewText(R.id.cell_time_text, compactRangeLabel(slotStart, slotEnd));
            row.addView(R.id.table_row, timeCell);

            for (int dayIdx : activeDays) {
                ClassSession match = findClassInSlot(schedule, dayIdx, slotStart, slotEnd);
                RemoteViews classCell = new RemoteViews(context.getPackageName(), R.layout.cell_week_table_class);
                if (match != null) {
                    classCell.setViewVisibility(R.id.cell_class_text, View.VISIBLE);
                    classCell.setViewVisibility(R.id.cell_dot_image, View.GONE);
                    classCell.setTextViewText(R.id.cell_class_text, abbreviateName(match.name));
                    classCell.setInt(R.id.cell_class_text, "setTextColor", dayIdx == today ? RED : INK);
                } else {
                    classCell.setViewVisibility(R.id.cell_class_text, View.GONE);
                    classCell.setViewVisibility(R.id.cell_dot_image, View.VISIBLE);
                }
                row.addView(R.id.table_row, classCell);
            }
            rv.addView(R.id.week_table_container, row);
        }

        int remaining = occupiedRows.size() - shown;
        if (remaining > 0) {
            rv.addView(R.id.week_table_container, moreIndicatorRow(context,
                    "+" + remaining + " more time slot" + (remaining == 1 ? "" : "s")));
        }
    }

    /**
     * Every distinct class start/end minute across the week, sorted -- the grid's row
     * boundaries. Public so ScheduleImageExporter can build the same grid at full
     * resolution for the "save to gallery" export.
     */
    public static List<Integer> weekBreakpoints(List<ClassSession> schedule) {
        TreeSet<Integer> set = new TreeSet<>();
        for (ClassSession c : schedule) {
            set.add(c.start);
            set.add(c.end);
        }
        return new ArrayList<>(set);
    }

    /**
     * Days (in WEEK_ORDER order) that have at least one class this week. Public so
     * ScheduleImageExporter can drop the same empty columns from the gallery export.
     */
    public static List<Integer> activeWeekDays(List<ClassSession> schedule) {
        List<Integer> result = new ArrayList<>();
        for (int dayIdx : WEEK_ORDER) {
            boolean hasClasses = false;
            for (ClassSession c : schedule) if (c.days.contains(dayIdx)) { hasClasses = true; break; }
            if (hasClasses) result.add(dayIdx);
        }
        return result;
    }

    /**
     * Indices (into breakpoints, i.e. row r spans [breakpoints[r], breakpoints[r+1]))
     * of the rows that have a class on at least one of the given days. Public so
     * ScheduleImageExporter can skip the same empty gap rows in the gallery export.
     */
    public static List<Integer> occupiedRowIndices(List<ClassSession> schedule, List<Integer> breakpoints,
                                                    List<Integer> days) {
        List<Integer> result = new ArrayList<>();
        int totalRows = Math.max(0, breakpoints.size() - 1);
        for (int r = 0; r < totalRows; r++) {
            int slotStart = breakpoints.get(r);
            int slotEnd = breakpoints.get(r + 1);
            for (int dayIdx : days) {
                if (findClassInSlot(schedule, dayIdx, slotStart, slotEnd) != null) {
                    result.add(r);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * "No classes on: Sat, Sun" style note listing the days (in WEEK_ORDER order)
     * that have zero classes this week -- empty string if every day has at least
     * one. Shown under the grid so dropping those columns entirely (see
     * activeWeekDays()) reads as "there's nothing scheduled here", not as the
     * grid having quietly forgotten a day.
     */
    public static String noClassDaysLabel(List<Integer> activeDays) {
        StringBuilder sb = new StringBuilder();
        for (int dayIdx : WEEK_ORDER) {
            if (activeDays.contains(dayIdx)) continue;
            String label = DAY_LABELS[dayIdx];
            String titleCase = label.charAt(0) + label.substring(1).toLowerCase(Locale.US);
            if (sb.length() > 0) sb.append(", ");
            sb.append(titleCase);
        }
        return sb.length() == 0 ? "" : "No classes on: " + sb;
    }

    /** The class (if any) that fully spans [slotStart, slotEnd) on the given day. */
    public static ClassSession findClassInSlot(List<ClassSession> schedule, int dayIdx, int slotStart, int slotEnd) {
        for (ClassSession c : schedule) {
            if (!c.days.contains(dayIdx)) continue;
            if (c.start <= slotStart && slotEnd <= c.end) return c;
        }
        return null;
    }

    /**
     * Short subject+catalog-number label for the grid's narrow cells, e.g.
     * "Philo 1 THV-3" -> "PHILO 1", "KAS 1 THW2" -> "KAS 1". Just the class
     * name's first two whitespace-separated tokens, upper-cased -- the section
     * code (THV-3, THW2, etc.) is dropped since there's no room for it here.
     * Public so ScheduleImageExporter can render cells identically to the widget.
     */
    public static String abbreviateName(String name) {
        if (name == null) return "";
        String[] tokens = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, tokens.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens[i].toUpperCase(Locale.US));
        }
        return sb.toString();
    }

    // Shared pieces

    /**
     * Builds one class row for the Today widget's scrollable ListView. Rows that
     * come from a RemoteViewsFactory can't carry their own PendingIntent directly
     * (the platform throws if you try) -- they use setOnClickFillInIntent()
     * instead, merged into the single PendingIntentTemplate set on the ListView
     * itself (see bindTodayListAdapter()). Called from
     * TodayClassesRemoteViewsService.Factory.getViewAt(); public for that reason.
     */
    public static RemoteViews buildClassRowForAdapter(Context context, ClassSession c, boolean isNow, int index, boolean isLast) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row_class);
        // Last row: drop its own inter-row gap so it doesn't stack on top of
        // today_list_wrapper's outer bottom padding and read as an oversized gap.
        if (isLast) {
            row.setViewPadding(R.id.row_class_root, 0, 0, 0, 0);
        }
        row.setTextViewText(R.id.row_time, minToLabel(c.start) + "\n" + minToLabel(c.end));
        row.setTextViewText(R.id.row_name, c.name);
        String meta = c.displayRoom() + (c.instructor != null && !c.instructor.isEmpty() ? " \u00B7 " + c.instructor : "");
        row.setTextViewText(R.id.row_room, meta);
        row.setInt(R.id.row_root, "setBackgroundResource", isNow ? R.drawable.row_bg_now : R.drawable.row_bg);
        row.setInt(R.id.row_time, "setTextColor", isNow ? RED : INK_DIM);
        row.setImageViewResource(R.id.row_badge, isNow ? R.drawable.dot_red : R.drawable.dot_dim);

        // Tap a class row -> ask whether to open a maps app for its location.
        // Only wired up when there's an actual room set (not blank/TBA).
        String mapQuery = (c.room != null && !c.room.trim().isEmpty() && !c.room.equalsIgnoreCase("TBA"))
                ? c.room
                : null;
        if (mapQuery != null) {
            Intent fillIn = new Intent();
            fillIn.putExtra(WidgetActionActivity.EXTRA_ROOM, mapQuery);
            fillIn.putExtra(WidgetActionActivity.EXTRA_CLASS_NAME, c.name);
            // Unique data URI per row so the system doesn't collapse distinct
            // rows' fill-in intents together.
            fillIn.setData(Uri.parse("crsscheduler://room/" + Uri.encode(c.code) + "/" + c.start));
            row.setOnClickFillInIntent(R.id.row_root, fillIn);
        }
        return row;
    }

    /** Small dim "+N more" line appended to a list/grid that got truncated to fit. */
    private static RemoteViews moreIndicatorRow(Context context, String text) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row_more_indicator);
        row.setTextViewText(R.id.more_indicator_text, text);
        return row;
    }

    /**
     * Points the Today widget's ListView at TodayClassesRemoteViewsService and
     * sets up the single PendingIntentTemplate its rows' fill-in intents merge
     * into at tap time. The service Intent's data URI must be unique per widget
     * id -- otherwise the system can treat repeated calls as "the same adapter"
     * and skip reloading fresh data.
     *
     * Also pads the bottom of the list area by whatever fraction of a row's
     * height doesn't divide evenly into the space available, so the ListView
     * only ever has room to draw whole rows. Without this, a ListView happily
     * draws a partial row at its boundary -- normal, expected behavior for an
     * interactively-scrolled list, but since each row has rounded corners, a
     * partial one gets hard-clipped along a straight edge exactly where the
     * rounding should be, which reads as a visual glitch rather than "there's
     * more below, scroll for it".
     */
    private static void bindTodayListAdapter(Context context, RemoteViews rv, int appWidgetId, int availableDp) {
        Intent svcIntent = new Intent(context, TodayClassesRemoteViewsService.class);
        svcIntent.setData(Uri.parse("crsscheduler://today_list/" + appWidgetId));
        rv.setRemoteAdapter(R.id.today_list_listview, svcIntent);
        rv.setEmptyView(R.id.today_list_listview, R.id.today_list_empty);

        if (availableDp != Integer.MAX_VALUE) {
            int listAreaDp = Math.max(0, availableDp - TODAY_BASE_HEIGHT_DP);
            int leftoverDp = listAreaDp % TODAY_ROW_HEIGHT_DP;
            if (leftoverDp > 0) {
                // Split the reserved slack top/bottom instead of dumping it all
                // below the last row, so it doesn't read as an oversized margin.
                int leftoverPx = Math.round(leftoverDp * context.getResources().getDisplayMetrics().density);
                int topPx = leftoverPx / 2;
                int bottomPx = leftoverPx - topPx;
                rv.setViewPadding(R.id.today_list_wrapper, 0, topPx, 0, bottomPx);
            }
        }

        Intent templateIntent = new Intent(context, WidgetActionActivity.class);
        PendingIntent templatePi = PendingIntent.getActivity(
                context, 3, templateIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setPendingIntentTemplate(R.id.today_list_listview, templatePi);
    }

    private static void bindGear(Context context, RemoteViews rv) {
        Intent intent = new Intent(context, ConfigureActivity.class);
        intent.setData(Uri.parse("crsscheduler://configure/gear"));
        PendingIntent pi = PendingIntent.getActivity(
                context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_settings, pi);
    }

    private static void bindViewFullSchedule(Context context, RemoteViews rv) {
        Intent intent = new Intent(context, WeekScheduleActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.btn_view_full_schedule, pi);
    }

    /**
     * The header's brand label doubles as the "preview before start" on/off
     * switch whenever the semester is genuinely UPCOMING (the *raw* phase,
     * unaffected by the preview toggle itself) -- tapping it flips
     * SettingsStore's preview flag via a broadcast to the given provider.
     * Outside that state it's just static branding text, exactly as before
     * this feature existed, so there's no visual change for the common case
     * of a schedule that's already in session.
     *
     * Whenever it IS acting as the toggle, it's given the same outline/filled
     * chip treatment as the widgets' other tappable toggles (e.g. the TMRW
     * chip) so it actually reads as a button instead of plain text.
     */
    private static void bindHeaderBrandLabel(Context context, RemoteViews rv, Class<?> providerClass,
                                              String toggleAction, String defaultLabel,
                                              SettingsStore.SemesterPhase rawPhase, int requestCode) {
        if (rawPhase == SettingsStore.SemesterPhase.UPCOMING) {
            boolean previewOn = SettingsStore.isPreviewBeforeStartEnabled(context);
            rv.setTextViewText(R.id.header_brand_label, previewOn ? "PREVIEW" : defaultLabel);
            rv.setInt(R.id.header_brand_label, "setTextColor", previewOn ? BG : RED);
            rv.setInt(R.id.header_brand_label, "setBackgroundResource",
                    previewOn ? R.drawable.chip_filled_bg : R.drawable.chip_outline_bg);
            bindToggleTap(context, rv, R.id.header_brand_label, providerClass, toggleAction, requestCode);
        } else {
            rv.setTextViewText(R.id.header_brand_label, defaultLabel);
            rv.setInt(R.id.header_brand_label, "setTextColor", INK_DIM);
            rv.setInt(R.id.header_brand_label, "setBackgroundResource", android.R.color.transparent);
        }
    }

    /** Reflects the Today widget's "show tomorrow instead" state onto its list-title chip and wires its tap. */
    private static void bindTomorrowToggle(Context context, RemoteViews rv, boolean active) {
        rv.setTextViewText(R.id.btn_toggle_tomorrow, active ? "TODAY" : "TMRW");
        rv.setInt(R.id.btn_toggle_tomorrow, "setBackgroundResource",
                active ? R.drawable.chip_filled_bg : R.drawable.chip_outline_bg);
        rv.setInt(R.id.btn_toggle_tomorrow, "setTextColor", active ? BG : INK_DIM);
        bindToggleTap(context, rv, R.id.btn_toggle_tomorrow, TodayWidgetProvider.class,
                TodayWidgetProvider.ACTION_TOGGLE_TOMORROW, 7);
    }

    /**
     * Wires a plain broadcast PendingIntent to `providerClass` onto any view id --
     * the general-purpose mechanism behind the widgets' own tappable toggles
     * (preview-before-start, show-tomorrow). Each RemoteViews here is freshly
     * built from scratch on every call, so there's no stale click target left
     * behind from a previous state to worry about clearing.
     */
    private static void bindToggleTap(Context context, RemoteViews rv, int viewId, Class<?> providerClass,
                                       String action, int requestCode) {
        Intent intent = new Intent(context, providerClass);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(viewId, pi);
    }

    /**
     * The widget's current height in dp, in the host's normal (portrait) orientation, or
     * Integer.MAX_VALUE if unknown -- so an unbound/never-resized widget defaults to
     * showing everything rather than the most constrained state.
     *
     * Deliberately reads OPTION_APPWIDGET_MAX_HEIGHT, not OPTION_APPWIDGET_MIN_HEIGHT --
     * despite the name, MIN_HEIGHT is the widget's *landscape* height (the shorter of the
     * pair), while MAX_HEIGHT is the *portrait* height. A widget reports
     * minWidth/maxHeight for portrait and maxWidth/minHeight for landscape, so on a
     * normal (portrait) home screen, MAX_HEIGHT is the one that matches what's actually
     * on screen. Reading MIN_HEIGHT here fed in a shorter-than-real value, which made the
     * leftover-row padding math below overestimate how little room was left and clip off
     * a row that would have genuinely fit.
     */
    private static int currentPortraitHeightDp(Bundle options) {
        if (options == null) return Integer.MAX_VALUE;
        int h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1);
        return h > 0 ? h : Integer.MAX_VALUE;
    }

    public static int calendarDayToJs(int calendarDayOfWeek) {
        return calendarDayOfWeek - 1; // Calendar.SUNDAY=1..SATURDAY=7 -> JS Sun=0..Sat=6
    }

    public static String minToLabel(int min) {
        int h = min / 60, m = min % 60;
        String mer = h >= 12 ? "PM" : "AM";
        h = h % 12;
        if (h == 0) h = 12;
        return h + ":" + String.format(Locale.US, "%02d", m) + " " + mer;
    }

    /** "Aug 15" style label for a semester start/end date -- year omitted since it's always near-term. */
    public static String formatSemesterDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US));
    }

    /** Compact "10a" / "11:30a" / "1p" style label for the grid's narrow time column. */
    public static String compactTimeLabel(int min) {
        int h = min / 60, m = min % 60;
        String mer = h >= 12 ? "p" : "a";
        h = h % 12;
        if (h == 0) h = 12;
        return m == 0 ? (h + mer) : (h + ":" + String.format(Locale.US, "%02d", m) + mer);
    }

    /**
     * Compact "10-11:30a" / "11:30a-1p" style range label for a table row
     * spanning [startMin, endMin) -- shows when the slot ends, not just when
     * it starts, which was otherwise easy to misread as "the class starting
     * at this row's time runs the whole row" rather than "runs until the
     * second time shown". Drops the start time's a/p suffix when it matches
     * the end's (the usual written convention for a same-meridiem range) and
     * keeps both when they differ.
     *
     * Only used where a row has room for one line (the gallery export's wide
     * time column); the widget grid's narrow column instead stacks
     * compactTimeLabel(start) and compactTimeLabel(end) on two lines -- see
     * buildWeekTable().
     */
    public static String compactRangeLabel(int startMin, int endMin) {
        String startLabel = compactTimeLabel(startMin);
        String endLabel = compactTimeLabel(endMin);
        boolean sameMeridiem = (startMin / 60 >= 12) == (endMin / 60 >= 12);
        String start = sameMeridiem ? startLabel.substring(0, startLabel.length() - 1) : startLabel;
        return start + "\u2013" + endLabel;
    }
}
