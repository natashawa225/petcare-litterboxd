package com.litterboxd.app.checkin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns matching {@link HistoryTrigger} rules into follow-up question ids for today's session.
 * Past answers are keyed by ISO date (yyyy-MM-dd) → question id → chosen values.
 * A missing day breaks the streak (the cat must have logged that answer each of those days).
 */
public final class HistoryTriggerEvaluator {

    private HistoryTriggerEvaluator() {}

    /**
     * @param triggers      rules from the decision tree
     * @param checkinDate   the day being filled in (history looks at the days before this)
     * @param answersByDate past check-in answers for this cat (may include the checkinDate; ignored)
     * @return follow-up ids to inject, in trigger / declaration order, de-duplicated
     */
    public static List<String> evaluate(List<HistoryTrigger> triggers, LocalDate checkinDate,
                                        Map<String, Map<String, List<String>>> answersByDate) {
        Set<String> followUps = new LinkedHashSet<>();
        if (triggers == null || checkinDate == null || answersByDate == null) {
            return new ArrayList<>();
        }
        for (HistoryTrigger trigger : triggers) {
            if (matches(trigger, checkinDate, answersByDate)) {
                followUps.addAll(trigger.getAddFollowUps());
            }
        }
        return new ArrayList<>(followUps);
    }

    private static boolean matches(HistoryTrigger trigger, LocalDate checkinDate,
                                   Map<String, Map<String, List<String>>> answersByDate) {
        for (int i = 1; i <= trigger.getConsecutiveDays(); i++) {
            String day = checkinDate.minusDays(i).toString();
            Map<String, List<String>> answers = answersByDate.get(day);
            if (answers == null) {
                return false;
            }
            List<String> values = answers.get(trigger.getQuestionId());
            if (values == null || !values.contains(trigger.getValue())) {
                return false;
            }
        }
        return true;
    }
}
