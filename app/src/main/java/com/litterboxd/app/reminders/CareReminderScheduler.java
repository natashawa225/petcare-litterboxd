package com.litterboxd.app.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;

public final class CareReminderScheduler {
    public static final String ACTION_CARE_TASK_REMINDER = "com.litterboxd.app.ACTION_CARE_TASK_REMINDER";
    public static final String EXTRA_TASK_ID = "task_id";

    private CareReminderScheduler() {}

    public static void schedule(Context context, CareTask task) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null || task == null) {
            return;
        }
        PendingIntent pendingIntent = pendingIntent(context, task.getId());
        if (task.getId() <= 0 || !task.hasReminder()) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        long now = System.currentTimeMillis();
        boolean complete = task.isCompleteNow(now);
        long triggerAt = task.getFrequency().nextReminderMillis(
                task.getReminderHour(), task.getReminderMinute(), now, complete);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void cancel(Context context, long taskId) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(pendingIntent(context, taskId));
    }

    public static void cancelForCat(Context context, CareTaskDao dao, long catId) {
        for (CareTask task : dao.getTasksForCat(catId)) {
            cancel(context, task.getId());
        }
    }

    public static void rescheduleAll(Context context) {
        CareTaskDao dao = new CareTaskDao(LitterboxdDbHelper.getInstance(context));
        for (CareTask task : dao.getAllTasks()) {
            schedule(context, task);
        }
    }

    private static PendingIntent pendingIntent(Context context, long taskId) {
        Intent intent = new Intent(context, CareTaskReminderReceiver.class);
        intent.setAction(ACTION_CARE_TASK_REMINDER);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        int requestCode = (int) (taskId & 0x7fffffff);
        return PendingIntent.getBroadcast(
                context.getApplicationContext(),
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
