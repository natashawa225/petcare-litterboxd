package com.litterboxd.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class LitterboxdApplication extends Application {
    /** Channel for Feature 2 (care reminders). Created at startup so it exists before any notification is posted. */
    public static final String CARE_REMINDERS_CHANNEL_ID = "care_reminders";
    private static final String OLD_CHANNEL_ID = "litterboxd_feeding_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    // minSdk is 26, so NotificationChannel (API 26+) is always available.
    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_care_reminders_name);
        String description = getString(R.string.channel_care_reminders_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CARE_REMINDERS_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            // Remove the channel created under the previous ID on existing installs.
            notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
