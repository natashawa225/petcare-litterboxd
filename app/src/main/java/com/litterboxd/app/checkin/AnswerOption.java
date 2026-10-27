package com.litterboxd.app.checkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One tappable answer (a chip). Plain Java (no Android imports) so it can be unit-tested on the JVM.
 */
public class AnswerOption {
    private final String value;
    private final String labelKey;
    private final List<String> followUpIds;
    private final boolean normal;

    /**
     * @param value       the stable value stored for this answer, e.g. "less"
     * @param labelKey    name of the string resource shown on the chip
     * @param followUpIds ids of follow-up questions this answer leads to (may be empty or null)
     * @param normal      true if this answer means "everything is normal" for its question
     */
    public AnswerOption(String value, String labelKey, List<String> followUpIds, boolean normal) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Answer option needs a value");
        }
        if (labelKey == null || labelKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Answer option '" + value + "' needs a label key");
        }
        this.value = value;
        this.labelKey = labelKey;
        this.followUpIds = followUpIds == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(followUpIds));
        this.normal = normal;
    }

    public AnswerOption(String value, String labelKey, List<String> followUpIds) {
        this(value, labelKey, followUpIds, false);
    }

    public String getValue() {
        return value;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public List<String> getFollowUpIds() {
        return followUpIds;
    }

    public boolean isNormal() {
        return normal;
    }
}
