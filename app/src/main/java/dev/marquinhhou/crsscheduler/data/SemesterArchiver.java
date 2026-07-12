package dev.marquinhhou.crsscheduler.data;

import android.content.Context;

import java.time.LocalDate;
import java.util.List;

import dev.marquinhhou.crsscheduler.model.ClassSession;

/**
 * Auto-archives the live schedule once its configured semesterEnd date has
 * passed, so "no end date yet" -> off -> pick an end date actually means
 * something: the schedule doesn't just sit there showing an ENDED gate
 * forever, it gets moved to Saved Schedules and cleared, same as a manual
 * Clear from ConfigureActivity would do.
 *
 * Guarded by KEY_LAST_AUTO_ARCHIVED_END so this only fires once per
 * configured end date -- safe to call on every widget refresh (every ~15
 * min) without re-archiving an already-empty schedule repeatedly.
 */
public final class SemesterArchiver {

    private SemesterArchiver() {}

    public static void archiveIfSemesterEnded(Context context) {
        LocalDate end = SettingsStore.getSemesterEnd(context);
        if (end == null) return; // "ongoing" -- nothing to auto-archive
        if (SettingsStore.currentSemesterPhase(context) != SettingsStore.SemesterPhase.ENDED) return;
        if (end.equals(SettingsStore.getLastAutoArchivedEnd(context))) return; // already handled this end date

        List<ClassSession> current = ScheduleStore.load(context);
        if (!current.isEmpty()) {
            ScheduleHistoryStore.archive(context, current);
            ScheduleStore.clear(context);
        }
        SettingsStore.setLastAutoArchivedEnd(context, end);
    }
}
