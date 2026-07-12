package dev.marquinhhou.crsscheduler.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** AlarmManager alarms don't survive a reboot -- this puts class reminders back. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ClassReminderScheduler.rescheduleAll(context);
        }
    }
}
