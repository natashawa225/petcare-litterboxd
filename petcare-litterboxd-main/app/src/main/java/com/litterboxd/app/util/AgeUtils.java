package com.litterboxd.app.util;

import java.util.Calendar;

public class AgeUtils {

    /** Age in whole months, from the birthday if known, otherwise from the approximate age. */
    private static int totalMonths(Long birthdayMs, int approxYears, int approxMonths) {
        if (birthdayMs == null || birthdayMs <= 0) {
            return approxYears * 12 + approxMonths;
        }
        Calendar dob = Calendar.getInstance();
        dob.setTimeInMillis(birthdayMs);
        Calendar now = Calendar.getInstance();
        int totalMonths = (now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)) * 12
                + (now.get(Calendar.MONTH) - dob.get(Calendar.MONTH));
        if (now.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH)) {
            totalMonths--;
        }
        return Math.max(totalMonths, 0);
    }

    public static String calculateLifeStage(Long birthdayMs, int approxYears, int approxMonths) {
        int totalMonths = totalMonths(birthdayMs, approxYears, approxMonths);

        // AAHA/AAFP life stages:
        // Kitten: 0 to < 12 months (0-1 yr)
        // Young Adult: 1 to < 7 years (12 to 83 months)
        // Mature Adult: 7 to < 11 years (84 to 131 months)
        // Senior: 11 to < 15 years (132 to 179 months)
        // Geriatric: 15+ years (180+ months)

        if (totalMonths < 12) {
            return "Kitten";
        } else if (totalMonths < 84) {
            return "Young Adult";
        } else if (totalMonths < 132) {
            return "Mature Adult";
        } else if (totalMonths < 180) {
            return "Senior";
        } else {
            return "Geriatric";
        }
    }

    public static String getAgeDisplay(Long birthdayMs, int approxYears, int approxMonths) {
        int totalMonths = totalMonths(birthdayMs, approxYears, approxMonths);

        int years = totalMonths / 12;
        int months = totalMonths % 12;

        if (years > 0 && months > 0) {
            return years + "y " + months + "m";
        } else if (years > 0) {
            return years + (years == 1 ? " year" : " years");
        } else {
            return months + (months == 1 ? " month" : " months");
        }
    }
}
