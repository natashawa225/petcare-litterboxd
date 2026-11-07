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
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.ui.home.HomeActivity;
import com.litterboxd.app.util.AppExecutors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One notice if any cat still has today's checklist open. Sends nothing when every cat is done. */
public class TodayListReminderReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 0x544F4442;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TodayListReminderScheduler.ACTION_TODAY_LIST_REMINDER.equals(intent.getAction())) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                AppPrefs prefs = new AppPrefs(appContext);
                if (!prefs.isTodayListReminderEnabled()) return;
                LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(appContext);
                List<CareTask> tasks = new CareTaskDao(db).getAllTasks();
                Map<Long, String> names = new HashMap<>();
                for (Cat cat : new CatDao(db).getAllCats()) {
                    names.put(cat.getId(), cat.getName());
                }
                List<String> unfinished = ReminderGrouping.unfinishedTodayCatNames(
                        tasks, names, System.currentTimeMillis());
                if (!unfinished.isEmpty()) {
                    showNotification(appContext, ReminderGrouping.joinNames(unfinished));
                }
                TodayListReminderScheduler.scheduleFromPrefs(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, String catNames) {
        Intent openHome = new Intent(context, HomeActivity.class);
        openHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openHome,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String body = context.getString(R.string.notification_today_incomplete, catNames);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, LitterboxdApplication.CARE_REMINDERS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cat_face)
                .setContentTitle(context.getString(R.string.notification_today_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS may be denied on API 33+.
        }
    }
}
