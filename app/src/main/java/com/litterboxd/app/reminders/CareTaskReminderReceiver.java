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
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.CareTask;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.model.TaskFrequency;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(appContext);
                    List<CareTask> group = ReminderGrouping.incompleteSameSlot(task, dao.getAllTasks(), now);
                    if (group.isEmpty()) {
                        group = new ArrayList<>();
                        group.add(task);
                    }
                    Map<Long, String> namesById = new HashMap<>();
                    for (Cat cat : new CatDao(db).getAllCats()) {
                        namesById.put(cat.getId(), cat.getName());
                    }
                    List<String> catNames = new ArrayList<>();
                    for (CareTask member : group) {
                        String name = namesById.get(member.getCatId());
                        if (name != null && !name.trim().isEmpty() && !catNames.contains(name)) {
                            catNames.add(name.trim());
                        }
                    }
                    java.util.Collections.sort(catNames, String.CASE_INSENSITIVE_ORDER);
                    boolean merged = group.size() > 1 && catNames.size() > 1;
                    boolean firstForSlot = !merged
                            || new AppPrefs(appContext).claimReminderSlot(
                            ReminderGrouping.slotKey(task), ReminderGrouping.dayKey(now));
                    if (firstForSlot) {
                        showNotification(appContext, task, catNames, merged);
                    }
                }
                CareReminderScheduler.schedule(appContext, task);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, CareTask task, List<String> catNames, boolean merged) {
        int notificationId = merged
                ? groupedNotificationId(ReminderGrouping.slotKey(task))
                : (int) (task.getId() & 0x7fffffff);
        Intent openHome = new Intent(context, HomeActivity.class);
        openHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openHome,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String cats = ReminderGrouping.joinNames(catNames);
        String body;
        String title;
        if (merged) {
            title = context.getString(R.string.notification_task_title);
            if (task.getFrequency() == TaskFrequency.MONTHLY) {
                body = context.getString(R.string.notification_task_due_month_cats, task.getName(), cats);
            } else if (task.getFrequency() == TaskFrequency.ONCE) {
                body = context.getString(R.string.notification_task_due_now_cats, task.getName(), cats);
            } else {
                body = context.getString(R.string.notification_task_due_today_cats, task.getName(), cats);
            }
        } else if (task.getFrequency() == TaskFrequency.MONTHLY) {
            body = context.getString(R.string.notification_task_due_month, task.getName());
            title = cats.isEmpty()
                    ? context.getString(R.string.notification_task_title)
                    : context.getString(R.string.notification_task_title_cat, cats);
        } else if (task.getFrequency() == TaskFrequency.ONCE) {
            body = context.getString(R.string.notification_task_due_now, task.getName());
            title = cats.isEmpty()
                    ? context.getString(R.string.notification_task_title)
                    : context.getString(R.string.notification_task_title_cat, cats);
        } else {
            body = context.getString(R.string.notification_task_due_today, task.getName());
            title = cats.isEmpty()
                    ? context.getString(R.string.notification_task_title)
                    : context.getString(R.string.notification_task_title_cat, cats);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, LitterboxdApplication.CARE_REMINDERS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cat_face)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS may be denied on API 33+.
        }
    }

    /** Stable id so a second cat's alarm updates the same notice instead of adding another. */
    private static int groupedNotificationId(String slotKey) {
        return 0x50000000 | (slotKey.hashCode() & 0x0fffffff);
    }
}
