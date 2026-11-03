package com.litterboxd.app.checkin;

import java.util.List;
import java.util.Map;

/** Soft calendar mark for a saved check-in day (not a health diagnosis). */
public enum CheckInDayMark {
    /** No check-in that day. */
    NONE,
    /** Logged; every chip group that was answered used its "normal" option. */
    NORMAL,
    /** Logged; at least one non-normal chip (something was noted). */
    NOTED;

    public static CheckInDayMark classify(DecisionTree tree, Map<String, List<String>> answers) {
        if (tree == null || answers == null || answers.isEmpty()) {
            return NONE;
        }
        boolean sawGroup = false;
        boolean anyOff = false;
        for (Question group : tree.getGroups()) {
            List<String> values = answers.get(group.getId());
            if (values == null || values.isEmpty()) continue;
            sawGroup = true;
            AnswerOption normal = group.getNormalOption();
            for (String value : values) {
                if (normal == null || !normal.getValue().equals(value)) {
                    anyOff = true;
                    break;
                }
            }
        }
        if (!sawGroup) {
            return NOTED;
        }
        return anyOff ? NOTED : NORMAL;
    }
}
