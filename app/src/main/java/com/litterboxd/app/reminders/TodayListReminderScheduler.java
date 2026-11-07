package com.litterboxd.app.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.litterboxd.app.data.session.AppPrefs;

import java.util.Calendar;

/** One daily alarm for the unfinished Today's care tasks list. Off means no alarm. */
public final class TodayListReminderScheduler {

    public static final String ACTION_TODAY_LIST_REMINDER = "com.litterboxd.app.ACTION_TODAY_LIST_REMINDER";
    private static final int REQUEST_CODE = 0x544F4441;

    private TodayListReminderScheduler() {}

    public static void scheduleFromPrefs(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = pendingIntent(context);
        AppPrefs prefs = new AppPrefs(context);
        if (!prefs.isTodayListReminderEnabled()) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        long triggerAt = nextMillis(
                prefs.getTodayListReminderHour(),
                prefs.getTodayListReminderMinute(),
                System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static long nextMillis(int hour, int minute, long nowMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        return next.getTimeInMillis();
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, TodayListReminderReceiver.class);
        intent.setAction(ACTION_TODAY_LIST_REMINDER);
        return PendingIntent.getBroadcast(
                context.getApplicationContext(),
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
