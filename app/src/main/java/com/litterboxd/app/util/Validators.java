package com.litterboxd.app.util;

public class Validators {

    public static boolean isValidCatName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    public static boolean isValidWeight(double weight) {
        return weight >= 0.5 && weight <= 15.0;
    }

    public static boolean isValidPortion(double portion) {
        return portion > 0.0 && portion <= 1000.0;
    }

    public static boolean isValidOwnerName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    /**
     * Parses a user-typed decimal such as "4.2" or "4,2" (comma keyboards).
     * Returns null for empty or non-numeric input instead of throwing.
     */
    public static Double parseDecimal(String text) {
        if (text == null) return null;
        String s = text.trim().replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
