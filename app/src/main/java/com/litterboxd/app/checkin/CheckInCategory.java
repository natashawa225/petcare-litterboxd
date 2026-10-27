package com.litterboxd.app.checkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A titled section of the symptom screen holding one or more chip groups, e.g. "Appetite & water". */
public class CheckInCategory {
    private final String id;
    private final String titleKey;
    private final List<Question> groups;

    public CheckInCategory(String id, String titleKey, List<Question> groups) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Category needs an id");
        }
        if (titleKey == null || titleKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Category '" + id + "' needs a title key");
        }
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("Category '" + id + "' needs at least one group");
        }
        this.id = id;
        this.titleKey = titleKey;
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
    }

    public String getId() {
        return id;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public List<Question> getGroups() {
        return groups;
    }
}
