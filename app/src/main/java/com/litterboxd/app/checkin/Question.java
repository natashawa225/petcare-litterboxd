package com.litterboxd.app.checkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A chip group on the symptom screen, or a follow-up question on the second screen.
 * Plain Java (no Android imports) so it can be unit-tested on the JVM.
 */
public class Question {
    private final String id;
    private final String textKey;
    private final List<AnswerOption> options;
    private final SelectionMode mode;

    /**
     * @param id      unique id, e.g. "appetite"
     * @param textKey name of the string resource holding the title or question text
     * @param options at least two answers; values must be unique and at most one may be "normal"
     * @param mode    SINGLE or MULTI
     */
    public Question(String id, String textKey, List<AnswerOption> options, SelectionMode mode) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Question needs an id");
        }
        if (textKey == null || textKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Question '" + id + "' needs a text key");
        }
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("Question '" + id + "' needs at least two options");
        }
        Set<String> seen = new HashSet<>();
        int normalCount = 0;
        for (AnswerOption option : options) {
            if (!seen.add(option.getValue())) {
                throw new IllegalArgumentException(
                        "Question '" + id + "' has two options with value '" + option.getValue() + "'");
            }
            if (option.isNormal()) {
                normalCount++;
            }
        }
        if (normalCount > 1) {
            throw new IllegalArgumentException("Question '" + id + "' has more than one normal option");
        }
        this.id = id;
        this.textKey = textKey;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.mode = mode == null ? SelectionMode.SINGLE : mode;
    }

    public Question(String id, String textKey, List<AnswerOption> options) {
        this(id, textKey, options, SelectionMode.SINGLE);
    }

    public String getId() {
        return id;
    }

    public String getTextKey() {
        return textKey;
    }

    public List<AnswerOption> getOptions() {
        return options;
    }

    public SelectionMode getMode() {
        return mode;
    }

    /** Returns the option with this value, or null if there is none. */
    public AnswerOption getOption(String value) {
        for (AnswerOption option : options) {
            if (option.getValue().equals(value)) {
                return option;
            }
        }
        return null;
    }

    /** Returns the option marked "normal", or null if this question has none. */
    public AnswerOption getNormalOption() {
        for (AnswerOption option : options) {
            if (option.isNormal()) {
                return option;
            }
        }
        return null;
    }
}
