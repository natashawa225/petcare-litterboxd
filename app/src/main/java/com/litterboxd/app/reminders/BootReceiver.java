package com.litterboxd.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.litterboxd.app.util.AppExecutors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
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
