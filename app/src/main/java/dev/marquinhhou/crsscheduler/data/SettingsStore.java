package dev.marquinhhou.crsscheduler.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Small settings store: the optional campus name used to scope Maps searches,
 * the first-run Terms gate, and the optional overall date range ("semester") the
 * loaded schedule applies to.
 *
 * Deliberately named "semester", not "term", to avoid reading like the Terms
 * gate above. This range is purely a display-layer gate -- it doesn't touch
 * ScheduleStore's data at all, it just tells the live widgets/in-app schedule
 * view whether "today" falls inside [semesterStart, semesterEnd] (or
 * [semesterStart, ...) if no end date was set, i.e. "still ongoing"), so they
 * can show a "hasn't started yet" / "this semester has ended" state instead of
 * a live schedule that isn't actually in session. Leaving it unset (the
 * default) skips this check entirely, so nothing changes for anyone who
 * doesn't set it.
 */
public final class SettingsStore {

    private static final String PREFS = "nothing_schedule_settings";
    private static final String KEY_CAMPUS = "campus_hint";
    private static final String KEY_TERMS_ACCEPTED = "terms_accepted";
    private static final String KEY_SEMESTER_START = "semester_start_epoch_day";
    private static final String KEY_SEMESTER_END = "semester_end_epoch_day";
    private static final String KEY_LAST_AUTO_ARCHIVED_END = "semester_last_auto_archived_end_epoch_day";
    private static final String KEY_PREVIEW_BEFORE_START = "widget_preview_before_start";
    private static final String KEY_SHOW_TOMORROW = "widget_show_tomorrow";
    private static final String KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes";
    private static final String KEY_REMINDER_REQUEST_CODES = "reminder_scheduled_request_codes";

    private SettingsStore() {}

    public static String getCampusHint(Context context) {
        return prefs(context).getString(KEY_CAMPUS, "");
    }

    public static void setCampusHint(Context context, String campus) {
        prefs(context).edit().putString(KEY_CAMPUS, campus).apply();
    }

    public static boolean hasAcceptedTerms(Context context) {
        return prefs(context).getBoolean(KEY_TERMS_ACCEPTED, false);
    }

    public static void setAcceptedTerms(Context context, boolean accepted) {
        prefs(context).edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply();
    }

    /** The day the current schedule starts applying, or null if not set. */
    public static LocalDate getSemesterStart(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SEMESTER_START) ? LocalDate.ofEpochDay(p.getLong(KEY_SEMESTER_START, 0)) : null;
    }

    /** Pass null to clear it. */
    public static void setSemesterStart(Context context, LocalDate date) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (date == null) e.remove(KEY_SEMESTER_START); else e.putLong(KEY_SEMESTER_START, date.toEpochDay());
        e.apply();
    }

    /** The last day the current schedule applies, or null if it's ongoing / not set. */
    public static LocalDate getSemesterEnd(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SEMESTER_END) ? LocalDate.ofEpochDay(p.getLong(KEY_SEMESTER_END, 0)) : null;
    }

    /** Pass null to clear it (meaning "no end date / still ongoing"). */
    public static void setSemesterEnd(Context context, LocalDate date) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (date == null) e.remove(KEY_SEMESTER_END); else e.putLong(KEY_SEMESTER_END, date.toEpochDay());
        e.apply();
    }

    public enum SemesterPhase {
        NOT_SET,    // no start date configured -- schedule always treated as in session
        UPCOMING,   // before semesterStart
        ACTIVE,     // on/after semesterStart, and on/before semesterEnd if one is set
        ENDED       // after semesterEnd
    }

    /** Where "today" falls relative to the configured semester range. */
    public static SemesterPhase currentSemesterPhase(Context context) {
        LocalDate start = getSemesterStart(context);
        if (start == null) return SemesterPhase.NOT_SET;
        LocalDate today = LocalDate.now();
        if (today.isBefore(start)) return SemesterPhase.UPCOMING;
        LocalDate end = getSemesterEnd(context);
        if (end != null && today.isAfter(end)) return SemesterPhase.ENDED;
        return SemesterPhase.ACTIVE;
    }

    /**
     * Same as currentSemesterPhase(), except an UPCOMING phase is folded into
     * ACTIVE when the widget-level "preview before start" toggle is on. Used
     * anywhere the *content* gate lives (widgets, the full schedule screen);
     * currentSemesterPhase() itself is left alone so things like the "Semester
     * hasn't started yet" copy and the toggle's own on/off state can still
     * tell a real UPCOMING apart from an ACTIVE-via-preview one.
     */
    public static SemesterPhase effectiveDisplayPhase(Context context) {
        SemesterPhase raw = currentSemesterPhase(context);
        if (raw == SemesterPhase.UPCOMING && isPreviewBeforeStartEnabled(context)) {
            return SemesterPhase.ACTIVE;
        }
        return raw;
    }

    /** Widget-tappable toggle: show the live schedule even before semesterStart. */
    public static boolean isPreviewBeforeStartEnabled(Context context) {
        return prefs(context).getBoolean(KEY_PREVIEW_BEFORE_START, false);
    }

    public static void setPreviewBeforeStartEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_PREVIEW_BEFORE_START, enabled).apply();
    }

    /** Widget-tappable toggle: the Today widget's class list shows tomorrow instead of today. */
    public static boolean isShowTomorrowEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_TOMORROW, false);
    }

    public static void setShowTomorrowEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHOW_TOMORROW, enabled).apply();
    }

    /** Marks that we've already auto-archived the schedule for this semester end date, so it only happens once. */
    public static LocalDate getLastAutoArchivedEnd(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_LAST_AUTO_ARCHIVED_END) ? LocalDate.ofEpochDay(p.getLong(KEY_LAST_AUTO_ARCHIVED_END, 0)) : null;
    }

    public static void setLastAutoArchivedEnd(Context context, LocalDate date) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (date == null) e.remove(KEY_LAST_AUTO_ARCHIVED_END); else e.putLong(KEY_LAST_AUTO_ARCHIVED_END, date.toEpochDay());
        e.apply();
    }

    /** Minutes before each class to notify, or 0 for "off". */
    public static int getReminderLeadMinutes(Context context) {
        return prefs(context).getInt(KEY_REMINDER_LEAD_MINUTES, 0);
    }

    public static void setReminderLeadMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_REMINDER_LEAD_MINUTES, minutes).apply();
    }

    /**
     * The AlarmManager request codes ClassReminderScheduler currently has
     * pending, so it can cancel exactly those before rescheduling rather than
     * guessing. Not meant to be read/written outside that class.
     */
    public static List<Integer> getScheduledReminderRequestCodes(Context context) {
        String json = prefs(context).getString(KEY_REMINDER_REQUEST_CODES, null);
        List<Integer> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getInt(i));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    public static void setScheduledReminderRequestCodes(Context context, List<Integer> codes) {
        JSONArray arr = new JSONArray();
        for (int code : codes) arr.put(code);
        prefs(context).edit().putString(KEY_REMINDER_REQUEST_CODES, arr.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
