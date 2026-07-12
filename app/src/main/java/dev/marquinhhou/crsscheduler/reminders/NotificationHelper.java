package dev.marquinhhou.crsscheduler.reminders;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/** One-time setup of the notification channel class reminders post to. */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "class_reminders";

    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Class reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("A heads-up a few minutes before each class starts.");
        nm.createNotificationChannel(channel);
    }
}
