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
}
