package dev.marquinhhou.crsscheduler.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;

/**
 * Every reminder/tick alarm this app sets is anchored to a wall-clock instant computed at
 * schedule time (see ClassReminderScheduler.nextOccurrenceMillis() and
 * WidgetRefreshScheduler.scheduleNextTransitionTick()). If the user manually sets the clock,
 * changes timezone, or the day just rolls over past midnight, those already-armed alarms can
 * end up targeting the wrong real-world moment until something re-anchors them.
 *
 * <p>This mirrors BootReceiver's approach (rebuild everything from scratch) rather than trying
 * to patch existing alarms in place -- rescheduleAll()/updateAllWidgets() are cheap and already
 * idempotent, and DATE_CHANGED firing here doubles as the widgets' immediate "new day" refresh
 * instead of waiting for the next 15-minute tick.
 */
public class TimeChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {
            WidgetRefreshScheduler.updateAllWidgets(context);
        }
    }
}
