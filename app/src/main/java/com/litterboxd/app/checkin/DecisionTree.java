package com.litterboxd.app.checkin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The check-in content: categories of chip groups, plus the follow-up questions the chips lead to.
 *
 *  - Groups (inside categories) are shown on the first screen as chips.
 *  - Follow-up questions are shown on the second screen, only when a selected chip names them.
 *    A follow-up answer can name further follow-ups.
 *
 * The constructor validates the whole tree, so a broken tree can never be used: ids are unique,
 * every chip points at an existing follow-up (never at a group), follow-ups are single-choice,
 * every follow-up is reachable from some chip, and follow-ups can't loop.
 * Plain Java (no Android imports) so it can be unit-tested on the JVM.
 */
public class DecisionTree {
    private final List<CheckInCategory> categories;
    private final List<Question> groups;
    private final Map<String, Question> questions;
    private final Set<String> groupIds;

    public DecisionTree(List<CheckInCategory> categories, List<Question> followUpQuestions) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("Tree needs at least one category");
        }
        List<Question> allGroups = new ArrayList<>();
        Map<String, Question> map = new LinkedHashMap<>();
        Set<String> groupIdSet = new HashSet<>();
        for (CheckInCategory category : categories) {
            for (Question group : category.getGroups()) {
                add(map, group);
                groupIdSet.add(group.getId());
                allGroups.add(group);
            }
        }
        for (Question followUp : followUpQuestions) {
            add(map, followUp);
        }
        this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
        this.groups = Collections.unmodifiableList(allGroups);
        this.questions = Collections.unmodifiableMap(map);
        this.groupIds = Collections.unmodifiableSet(groupIdSet);
        validate();
    }

    private static void add(Map<String, Question> map, Question question) {
        if (map.put(question.getId(), question) != null) {
            throw new IllegalArgumentException("Duplicate question id: " + question.getId());
        }
    }

    public List<CheckInCategory> getCategories() {
        return categories;
    }

    /** Every chip group across all categories, in display order. */
    public List<Question> getGroups() {
        return groups;
    }

    /** Returns the group or follow-up question with this id, or null if there is none. */
    public Question getQuestion(String id) {
        return questions.get(id);
    }

    public boolean isGroup(String id) {
        return groupIds.contains(id);
    }

    /** Every group and follow-up question in the tree. */
    public Collection<Question> getQuestions() {
        return questions.values();
    }

    private void validate() {
        Set<String> referenced = new HashSet<>();
        for (Question q : questions.values()) {
            boolean isGroup = groupIds.contains(q.getId());
            if (!isGroup && q.getMode() != SelectionMode.SINGLE) {
                throw new IllegalArgumentException("Follow-up '" + q.getId() + "' must be single-choice");
            }
            for (AnswerOption option : q.getOptions()) {
                for (String followUpId : option.getFollowUpIds()) {
                    if (!questions.containsKey(followUpId)) {
                        throw new IllegalArgumentException("Question '" + q.getId()
                                + "' points to missing follow-up '" + followUpId + "'");
                    }
                    if (groupIds.contains(followUpId)) {
                        throw new IllegalArgumentException("Follow-up '" + followUpId
                                + "' is also a chip group");
                    }
                    referenced.add(followUpId);
                }
            }
        }

        for (String id : questions.keySet()) {
            if (!groupIds.contains(id) && !referenced.contains(id)) {
                throw new IllegalArgumentException("Follow-up '" + id + "' can never be asked");
            }
        }

        checkNoCycles();
    }

    /** Depth-first search over follow-up links; a link back to a question on the path is a loop. */
    private void checkNoCycles() {
        Map<String, Integer> state = new HashMap<>(); // 1 = on current path, 2 = finished
        for (String id : questions.keySet()) {
            visit(id, state);
        }
    }

    private void visit(String id, Map<String, Integer> state) {
        Integer current = state.get(id);
        if (current != null) {
            if (current == 1) {
                throw new IllegalArgumentException("Follow-up questions loop back to '" + id + "'");
            }
            return;
        }
        state.put(id, 1);
        for (AnswerOption option : questions.get(id).getOptions()) {
            for (String followUpId : option.getFollowUpIds()) {
                visit(followUpId, state);
            }
        }
        state.put(id, 2);
    }
}
