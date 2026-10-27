package com.litterboxd.app.checkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The owner's answers while filling in one check-in for one cat.
 *
 * Screen 1: chips are tapped with toggle(). Screen 2: the follow-up questions that the selected
 * chips lead to are listed by getActiveFollowUps() and answered with answerFollowUp().
 *
 * Follow-ups are always DERIVED from the current chip selection and follow-up answers, never
 * stored separately. So changing a chip on screen 1 automatically adds or removes follow-ups,
 * and the same session can be rebuilt after a rotation or when editing a saved check-in.
 * Plain Java (no Android imports) so it can be unit-tested on the JVM.
 */
public class CheckInSession {
    private final DecisionTree tree;
    private final Map<String, Set<String>> selections = new LinkedHashMap<>();
    private final Map<String, String> followUpAnswers = new LinkedHashMap<>();

    public CheckInSession(DecisionTree tree) {
        this.tree = tree;
    }

    // ---- Screen 1: chips ----

    /**
     * Taps a chip. Tapping a selected chip deselects it. In a SINGLE group the chip replaces the
     * previous choice. In a MULTI group chips add up, except that the "normal" chip clears the
     * others and any other chip clears "normal".
     */
    public void toggle(String groupId, String value) {
        Question group = requireGroup(groupId);
        AnswerOption option = requireOption(group, value);

        Set<String> chosen = selections.get(groupId);
        if (chosen == null) {
            chosen = new LinkedHashSet<>();
            selections.put(groupId, chosen);
        }

        if (chosen.contains(value)) {
            chosen.remove(value);
        } else {
            if (group.getMode() == SelectionMode.SINGLE || option.isNormal()) {
                chosen.clear();
            } else {
                AnswerOption normal = group.getNormalOption();
                if (normal != null) {
                    chosen.remove(normal.getValue());
                }
            }
            chosen.add(value);
        }

        if (chosen.isEmpty()) {
            selections.remove(groupId);
        }
    }

    public boolean isSelected(String groupId, String value) {
        Set<String> chosen = selections.get(groupId);
        return chosen != null && chosen.contains(value);
    }

    /** Sets the "normal" chip in every group that has one, replacing whatever was chosen there. */
    public void selectAllNormal() {
        for (Question group : tree.getGroups()) {
            AnswerOption normal = group.getNormalOption();
            if (normal != null) {
                Set<String> chosen = new LinkedHashSet<>();
                chosen.add(normal.getValue());
                selections.put(group.getId(), chosen);
            }
        }
    }

    /** True once at least one chip is selected. A check-in with nothing selected counts as not logged. */
    public boolean hasAnySelection() {
        return !selections.isEmpty();
    }

    // ---- Screen 2: follow-ups ----

    /**
     * The follow-up questions to show, in order: for each selected chip (in display order) the
     * follow-ups it names, and right after each answered follow-up the follow-ups its answer names.
     * A follow-up named twice is listed once.
     */
    public List<Question> getActiveFollowUps() {
        Set<String> ids = new LinkedHashSet<>();
        for (Question group : tree.getGroups()) {
            for (AnswerOption option : group.getOptions()) {
                if (isSelected(group.getId(), option.getValue())) {
                    for (String followUpId : option.getFollowUpIds()) {
                        collect(followUpId, ids);
                    }
                }
            }
        }
        List<Question> result = new ArrayList<>();
        for (String id : ids) {
            result.add(tree.getQuestion(id));
        }
        return result;
    }

    private void collect(String followUpId, Set<String> ids) {
        if (!ids.add(followUpId)) {
            return;
        }
        String answered = followUpAnswers.get(followUpId);
        if (answered != null) {
            AnswerOption option = tree.getQuestion(followUpId).getOption(answered);
            for (String next : option.getFollowUpIds()) {
                collect(next, ids);
            }
        }
    }

    public boolean hasFollowUps() {
        return !getActiveFollowUps().isEmpty();
    }

    /** Answers a follow-up. Answering with the value already chosen clears the answer. */
    public void answerFollowUp(String questionId, String value) {
        boolean active = false;
        for (Question q : getActiveFollowUps()) {
            if (q.getId().equals(questionId)) {
                active = true;
                break;
            }
        }
        if (!active) {
            throw new IllegalArgumentException("'" + questionId + "' is not an active follow-up");
        }
        requireOption(tree.getQuestion(questionId), value);

        if (value.equals(followUpAnswers.get(questionId))) {
            followUpAnswers.remove(questionId);
        } else {
            followUpAnswers.put(questionId, value);
        }
    }

    /** The answer chosen for this follow-up, or null. */
    public String getFollowUpAnswer(String questionId) {
        return followUpAnswers.get(questionId);
    }

    // ---- Result and restore ----

    /**
     * What to save: question id to the values chosen, chip groups first (display order, values in
     * the tree's option order), then the answered follow-ups that are still active.
     * Answers to follow-ups that are no longer active are left out.
     */
    public Map<String, List<String>> getAnswers() {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        for (Question group : tree.getGroups()) {
            Set<String> chosen = selections.get(group.getId());
            if (chosen == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (AnswerOption option : group.getOptions()) {
                if (chosen.contains(option.getValue())) {
                    values.add(option.getValue());
                }
            }
            answers.put(group.getId(), values);
        }
        for (Question followUp : getActiveFollowUps()) {
            String answer = followUpAnswers.get(followUp.getId());
            if (answer != null) {
                answers.put(followUp.getId(), Collections.singletonList(answer));
            }
        }
        return answers;
    }

    /**
     * Rebuilds the session from saved answers (a saved check-in, or state kept across rotation).
     * Ids or values that no longer exist in the tree are ignored.
     */
    public void restore(Map<String, List<String>> saved) {
        selections.clear();
        followUpAnswers.clear();
        for (Map.Entry<String, List<String>> entry : saved.entrySet()) {
            Question question = tree.getQuestion(entry.getKey());
            if (question == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (question.getOption(value) == null) {
                    continue;
                }
                if (tree.isGroup(question.getId())) {
                    Set<String> chosen = selections.get(question.getId());
                    if (chosen == null) {
                        chosen = new LinkedHashSet<>();
                        selections.put(question.getId(), chosen);
                    }
                    chosen.add(value);
                } else {
                    followUpAnswers.put(question.getId(), value);
                }
            }
        }
    }

    private Question requireGroup(String groupId) {
        if (!tree.isGroup(groupId)) {
            throw new IllegalArgumentException("'" + groupId + "' is not a chip group");
        }
        return tree.getQuestion(groupId);
    }

    private static AnswerOption requireOption(Question question, String value) {
        AnswerOption option = question.getOption(value);
        if (option == null) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not an option of '" + question.getId() + "'");
        }
        return option;
    }
}
