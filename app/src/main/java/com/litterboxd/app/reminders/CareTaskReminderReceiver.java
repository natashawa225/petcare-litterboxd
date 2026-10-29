package com.litterboxd.app.reminders;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.litterboxd.app.LitterboxdApplication;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CareTaskDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.util.AppExecutors;

public class CareTaskReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !CareReminderScheduler.ACTION_CARE_TASK_REMINDER.equals(intent.getAction())) {
            return;
        }
        final long taskId = intent.getLongExtra(CareReminderScheduler.EXTRA_TASK_ID, -1L);
        if (taskId <= 0) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                CareTaskDao dao = new CareTaskDao(LitterboxdDbHelper.getInstance(appContext));
                CareTask task = dao.getTaskById(taskId);
                if (task == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (!task.isCompleteNow(now) && task.hasReminder()) {
                    showNotification(appContext, task);
                }
                CareReminderScheduler.schedule(appContext, task);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, CareTask task) {
        Intent openHome = new Intent(context, HomeActivity.class);
        openHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (task.getId() & 0x7fffffff),
                openHome,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body;
        if (task.getFrequency() == TaskFrequency.MONTHLY) {
            body = context.getString(R.string.notification_task_due_month, task.getName());
        } else if (task.getFrequency() == TaskFrequency.ONCE) {
            body = context.getString(R.string.notification_task_due_now, task.getName());
        } else {
            body = context.getString(R.string.notification_task_due_today, task.getName());
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, LitterboxdApplication.CARE_REMINDERS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cat_face)
                .setContentTitle(context.getString(R.string.notification_task_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(context).notify((int) (task.getId() & 0x7fffffff), builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS may be denied on API 33+.
        }
    }
}
