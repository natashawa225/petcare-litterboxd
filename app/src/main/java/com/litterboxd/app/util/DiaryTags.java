package com.litterboxd.app.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Diary tags are stored as one comma-separated column of stable keys (e.g. "play,first_time").
 * The keys never change; their display labels live in strings.xml (diary_tag_labels), so a
 * renamed label does not break saved entries. Plain Java so it can be unit-tested.
 */
public final class DiaryTags {

    private DiaryTags() {}

    /** Joins keys for storage; returns null when there are none. Blank or comma-containing keys are skipped. */
    public static String encode(List<String> keys) {
        if (keys == null || keys.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || key.contains(",")) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(key.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Splits a stored value back into keys; null or empty gives an empty list. */
    public static List<String> decode(String stored) {
        List<String> keys = new ArrayList<>();
        if (stored == null) return keys;
        for (String part : stored.split(",")) {
            String key = part.trim();
            if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
        }
        return keys;
    }
}
