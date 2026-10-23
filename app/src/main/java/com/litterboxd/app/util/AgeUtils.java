package com.litterboxd.app.util;

import java.util.Calendar;

public class AgeUtils {

    public static String calculateLifeStage(Long birthdayMs, int approxYears, int approxMonths) {
        int totalMonths;
        if (birthdayMs != null && birthdayMs > 0) {
            Calendar dob = Calendar.getInstance();
            dob.setTimeInMillis(birthdayMs);
            Calendar now = Calendar.getInstance();
            int years = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            int months = now.get(Calendar.MONTH) - dob.get(Calendar.MONTH);
            totalMonths = years * 12 + months;
            if (now.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH)) {
                totalMonths--;
            }
            if (totalMonths < 0) totalMonths = 0;
        } else {
            totalMonths = approxYears * 12 + approxMonths;
        }

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
        int totalMonths;
        if (birthdayMs != null && birthdayMs > 0) {
            Calendar dob = Calendar.getInstance();
            dob.setTimeInMillis(birthdayMs);
            Calendar now = Calendar.getInstance();
            int years = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            int months = now.get(Calendar.MONTH) - dob.get(Calendar.MONTH);
            totalMonths = years * 12 + months;
            if (now.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH)) {
                totalMonths--;
            }
            if (totalMonths < 0) totalMonths = 0;
        } else {
            totalMonths = approxYears * 12 + approxMonths;
        }

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
