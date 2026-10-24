package com.litterboxd.app.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("hh:mm a", Locale.US);

    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public static String formatTime(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp));
    }
}
