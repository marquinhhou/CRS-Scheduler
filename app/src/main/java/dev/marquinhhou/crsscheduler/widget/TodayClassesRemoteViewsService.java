package dev.marquinhhou.crsscheduler.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;

/**
 * Backs the scrollable "TODAY'S CLASSES" list inside the Today widget's full
 * layout. A plain ScrollView isn't among the view types RemoteViews supports --
 * real in-widget scrolling only exists through a collection view (ListView,
 * GridView, StackView, AdapterViewFlipper) backed by a RemoteViewsFactory like
 * this one. Because rows come from an adapter rather than being addView()'d
 * directly, per-row taps can't carry their own individual PendingIntent (the
 * platform rejects setOnClickPendingIntent() inside a factory-provided row) --
 * instead each row calls setOnClickFillInIntent(), and WidgetRenderer sets a
 * single setPendingIntentTemplate() on the ListView itself that the system
 * merges each row's fill-in data into at tap time.
 */
public class TodayClassesRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private List<ClassSession> todays = new ArrayList<>();
        private int ongoingIndex = -1;

        Factory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            // Nothing to set up up-front; onDataSetChanged() below does the real
            // loading and is guaranteed to run at least once before getViewAt().
        }

        @Override
        public void onDataSetChanged() {
            SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(context);
            if (phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED) {
                todays = new ArrayList<>();
                ongoingIndex = -1;
                return;
            }

            boolean showTomorrow = SettingsStore.isShowTomorrowEnabled(context);
            List<ClassSession> schedule = ScheduleStore.load(context);
            Calendar now = Calendar.getInstance();
            int today = WidgetRenderer.calendarDayToJs(now.get(Calendar.DAY_OF_WEEK));
            int targetDay = showTomorrow ? (today + 1) % 7 : today;
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            List<ClassSession> list = new ArrayList<>();
            for (ClassSession c : schedule) if (c.days.contains(targetDay)) list.add(c);
            list.sort((a, b) -> Integer.compare(a.start, b.start));
            todays = list;

            // "Ongoing" only makes sense for today's own list -- a class
            // shown because it's tomorrow can never be the one happening now.
            int found = -1;
            if (!showTomorrow) {
                for (int i = 0; i < todays.size(); i++) {
                    ClassSession c = todays.get(i);
                    if (nowMin >= c.start && nowMin < c.end) { found = i; break; }
                }
            }
            ongoingIndex = found;
        }

        @Override
        public void onDestroy() {
            todays = new ArrayList<>();
        }

        @Override
        public int getCount() {
            return todays.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= todays.size()) {
                return new RemoteViews(context.getPackageName(), R.layout.row_more_indicator);
            }
            ClassSession c = todays.get(position);
            boolean isLast = position == todays.size() - 1;
            return WidgetRenderer.buildClassRowForAdapter(context, c, position == ongoingIndex, position, isLast);
        }

        @Override
        public RemoteViews getLoadingView() {
            return null; // default platform placeholder is fine while a row loads
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
