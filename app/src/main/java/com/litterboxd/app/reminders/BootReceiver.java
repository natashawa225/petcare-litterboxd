package com.litterboxd.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.litterboxd.app.util.AppExecutors;

/**
 * Re-creates every care-task alarm when the system drops or invalidates them: after a reboot,
 * after the app is updated, and when the user changes the clock or time zone (alarm times are
 * computed from local wall-clock time).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                CareReminderScheduler.rescheduleAll(appContext);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
