package dev.marquinhhou.crsscheduler.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SemesterArchiver;
import dev.marquinhhou.crsscheduler.reminders.ClassReminderScheduler;

/**
 * Shared alarm scheduling for both widgets, plus a one-call "refresh everything"
 * used after the schedule is parsed/edited/cleared in ConfigureActivity.
 *
 * Refreshes run every 15 min via setInexactRepeating() rather than an exact
 * alarm every minute -- this needs no special permission and is still subject
 * to Doze/App Standby batching, at the cost of which class is shown as
 * NOW/NEXT (and the ring's progress) being up to ~15 min stale between class
 * boundaries -- the same granularity most weather/calendar widgets use. The
 * live "Xm left"/"in Xh Ym" countdown itself doesn't have this problem: it's
 * a Chronometer (see WidgetRenderer.showHero()), which ticks every second in
 * the launcher process regardless of when this alarm last fired.
 *
 * ELAPSED_REALTIME_WAKEUP (rather than the non-waking ELAPSED_REALTIME) is
 * used so this alarm actually fires close to on schedule instead of being
 * deferred indefinitely until some unrelated wake event -- on a phone that
 * sits untouched, a non-waking alarm can end up hours stale, not just ~15 min.
 */
public final class WidgetRefreshScheduler {

    private static final long REFRESH_INTERVAL_MS = 15 * 60 * 1000L;

    private WidgetRefreshScheduler() {}

    public static void updateAllWidgets(Context context) {
        // Runs on every explicit change (ConfigureActivity) *and* every ~15
        // min tick, so both self-heal on their own over time: a semester
        // that just ended gets archived+cleared without the user opening the
        // app, and reminders re-anchor to pick up a semester that just
        // started, a schedule edit, or a lead-time change.
        SemesterArchiver.archiveIfSemesterEnded(context);
        ClassReminderScheduler.rescheduleAll(context);

        AppWidgetManager mgr = AppWidgetManager.getInstance(context);

        int[] todayIds = mgr.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class));
        for (int id : todayIds) {
            Bundle options = mgr.getAppWidgetOptions(id);
            mgr.updateAppWidget(id, WidgetRenderer.buildToday(context, options, id));
            // The class list is a real ListView backed by a RemoteViewsFactory --
            // it needs an explicit nudge to reload after the schedule changes,
            // since setRemoteAdapter() alone isn't guaranteed to on repeat calls.
            mgr.notifyAppWidgetViewDataChanged(id, R.id.today_list_listview);
        }

        int[] weekIds = mgr.getAppWidgetIds(new ComponentName(context, WeekWidgetProvider.class));
        for (int id : weekIds) {
            Bundle options = mgr.getAppWidgetOptions(id);
            mgr.updateAppWidget(id, WidgetRenderer.buildWeekSummary(context, options));
        }
    }

    static void scheduleTicks(Context context, Class<?> providerClass, String tickAction) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
                REFRESH_INTERVAL_MS,
                tickPendingIntent(context, providerClass, tickAction));
    }

    static void cancelTicks(Context context, Class<?> providerClass, String tickAction) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickPendingIntent(context, providerClass, tickAction));
    }

    private static PendingIntent tickPendingIntent(Context context, Class<?> providerClass, String tickAction) {
        Intent intent = new Intent(context, providerClass);
        intent.setAction(tickAction);
        return PendingIntent.getBroadcast(
                context, providerClass.getName().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
