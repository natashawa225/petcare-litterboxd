package com.litterboxd.app.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a", Locale.US);

    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public static String formatTime(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp));
    }

    /** A clock time such as 7 and 30 as "7:30 AM", matching the rest of the app. */
    public static String formatClock(int hour, int minute) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, hour);
        c.set(java.util.Calendar.MINUTE, minute);
        return TIME_FORMAT.format(c.getTime());
    }

    /** A stored "HH:mm" meal time as "7:30 AM"; anything unreadable is shown as it is. */
    public static String formatClock(String hhmm) {
        if (hhmm == null) return "";
        String[] parts = hhmm.trim().split(":");
        try {
            if (parts.length == 2) return formatClock(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return hhmm;
    }

    /** Grams without a pointless ".0": 70.0 -> "70", 12.5 -> "12.5". */
    public static String formatGrams(double grams) {
        return grams == Math.rint(grams) ? String.valueOf((long) grams) : String.valueOf(grams);
    }
}
