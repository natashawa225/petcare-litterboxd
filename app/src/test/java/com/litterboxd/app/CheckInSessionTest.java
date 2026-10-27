package com.litterboxd.app;

import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.CheckInCategory;
import com.litterboxd.app.checkin.CheckInSession;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.checkin.SelectionMode;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Small hand-built tree (no JSON, no Android):
 *   appetite (single): less -> vomiting, hiding | normal(normal) | more
 *   water    (single): less | normal(normal) | more -> urination
 *   symptoms (multi):  vomit -> vomit_count | hairball | none(normal)
 *   follow-ups: vomiting(none|once), hiding(no|yes -> vomit_count), urination(no|yes), vomit_count(once|repeated)
 */
public class CheckInSessionTest {

    private DecisionTree tree;
    private CheckInSession session;

    private static AnswerOption opt(String value, String... followUps) {
        return new AnswerOption(value, "label_" + value, Arrays.asList(followUps));
    }

    private static AnswerOption normal(String value) {
        return new AnswerOption(value, "label_" + value, null, true);
    }

    private static Question q(String id, AnswerOption... options) {
        return new Question(id, "text_" + id, Arrays.asList(options));
    }

    private static Question multi(String id, AnswerOption... options) {
        return new Question(id, "text_" + id, Arrays.asList(options), SelectionMode.MULTI);
    }

    @Before
    public void setUp() {
        CheckInCategory appetiteWater = new CheckInCategory("appetite_water", "title_aw", Arrays.asList(
                q("appetite", opt("less", "vomiting", "hiding"), normal("normal"), opt("more")),
                q("water", opt("less"), normal("normal"), opt("more", "urination"))));
        CheckInCategory digestion = new CheckInCategory("digestion", "title_d", Arrays.asList(
                multi("symptoms", opt("vomit", "vomit_count"), opt("hairball"), normal("none"))));
        tree = new DecisionTree(Arrays.asList(appetiteWater, digestion), Arrays.asList(
                q("vomiting", opt("none"), opt("once")),
                q("hiding", opt("no"), opt("yes", "vomit_count")),
                q("urination", opt("no"), opt("yes")),
                q("vomit_count", opt("once"), opt("repeated"))));
        session = new CheckInSession(tree);
    }

    private static List<String> ids(List<Question> questions) {
        List<String> ids = new ArrayList<>();
        for (Question q : questions) {
            ids.add(q.getId());
        }
        return ids;
    }

    // ---- chips ----

    @Test
    public void startsWithNothingSelected() {
        assertFalse(session.hasAnySelection());
        assertTrue(session.getAnswers().isEmpty());
        assertTrue(session.getActiveFollowUps().isEmpty());
    }

    @Test
    public void singleGroupReplacesThePreviousChoice() {
        session.toggle("appetite", "less");
        session.toggle("appetite", "more");
        assertFalse(session.isSelected("appetite", "less"));
        assertTrue(session.isSelected("appetite", "more"));
    }

    @Test
    public void tappingASelectedChipDeselectsIt() {
        session.toggle("appetite", "less");
        session.toggle("appetite", "less");
        assertFalse(session.isSelected("appetite", "less"));
        assertFalse(session.hasAnySelection());
    }

    @Test
    public void multiGroupAllowsSeveralChips() {
        session.toggle("symptoms", "vomit");
        session.toggle("symptoms", "hairball");
        assertTrue(session.isSelected("symptoms", "vomit"));
        assertTrue(session.isSelected("symptoms", "hairball"));
    }

    @Test
    public void normalChipClearsTheOthersAndAnyOtherChipClearsNormal() {
        session.toggle("symptoms", "vomit");
        session.toggle("symptoms", "hairball");
        session.toggle("symptoms", "none");
        assertTrue(session.isSelected("symptoms", "none"));
        assertFalse(session.isSelected("symptoms", "vomit"));
        assertFalse(session.isSelected("symptoms", "hairball"));

        session.toggle("symptoms", "hairball");
        assertFalse(session.isSelected("symptoms", "none"));
        assertTrue(session.isSelected("symptoms", "hairball"));
    }

    @Test
    public void allNormalSetsEveryNormalChipAndReplacesOtherChoices() {
        session.toggle("appetite", "less");
        session.toggle("symptoms", "vomit");
        session.selectAllNormal();
        assertTrue(session.isSelected("appetite", "normal"));
        assertFalse(session.isSelected("appetite", "less"));
        assertTrue(session.isSelected("water", "normal"));
        assertTrue(session.isSelected("symptoms", "none"));
        assertFalse(session.isSelected("symptoms", "vomit"));
        assertTrue(session.getActiveFollowUps().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void togglingAnUnknownGroupIsRejected() {
        session.toggle("banana", "x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void togglingAFollowUpAsAGroupIsRejected() {
        session.toggle("hiding", "yes");
    }

    @Test(expected = IllegalArgumentException.class)
    public void togglingAnUnknownValueIsRejected() {
        session.toggle("appetite", "banana");
    }

    // ---- follow-ups ----

    @Test
    public void selectedChipsBringTheirFollowUpsInOrder() {
        session.toggle("appetite", "less");
        session.toggle("water", "more");
        assertEquals(Arrays.asList("vomiting", "hiding", "urination"), ids(session.getActiveFollowUps()));
        assertTrue(session.hasFollowUps());
    }

    @Test
    public void deselectingTheChipRemovesItsFollowUps() {
        session.toggle("appetite", "less");
        session.toggle("appetite", "less");
        assertTrue(session.getActiveFollowUps().isEmpty());
    }

    @Test
    public void aFollowUpNamedByTwoChipsIsListedOnce() {
        session.toggle("symptoms", "vomit");   // -> vomit_count
        session.toggle("appetite", "less");    // -> vomiting, hiding
        session.answerFollowUp("hiding", "yes"); // -> vomit_count again
        List<String> active = ids(session.getActiveFollowUps());
        assertEquals(1, Collections.frequency(active, "vomit_count"));
    }

    @Test
    public void aFollowUpAnswerCanBringItsOwnFollowUpRightAfterIt() {
        session.toggle("appetite", "less");
        session.answerFollowUp("hiding", "yes");
        assertEquals(Arrays.asList("vomiting", "hiding", "vomit_count"), ids(session.getActiveFollowUps()));
    }

    @Test
    public void answeringAFollowUpTwiceWithTheSameValueClearsIt() {
        session.toggle("appetite", "less");
        session.answerFollowUp("vomiting", "once");
        assertEquals("once", session.getFollowUpAnswer("vomiting"));
        session.answerFollowUp("vomiting", "once");
        assertNull(session.getFollowUpAnswer("vomiting"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void answeringAnInactiveFollowUpIsRejected() {
        session.answerFollowUp("vomiting", "once");
    }

    @Test(expected = IllegalArgumentException.class)
    public void answeringAFollowUpWithAnUnknownValueIsRejected() {
        session.toggle("appetite", "less");
        session.answerFollowUp("vomiting", "banana");
    }

    // ---- result and restore ----

    @Test
    public void answersListGroupsFirstInDisplayOrderThenActiveFollowUps() {
        session.toggle("water", "more");
        session.toggle("appetite", "less");
        session.answerFollowUp("urination", "yes");
        session.answerFollowUp("vomiting", "once");

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("appetite", Collections.singletonList("less"));
        expected.put("water", Collections.singletonList("more"));
        expected.put("vomiting", Collections.singletonList("once"));
        expected.put("urination", Collections.singletonList("yes"));
        assertEquals(expected, session.getAnswers());
        assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(session.getAnswers().keySet()));
    }

    @Test
    public void multiGroupValuesComeOutInTheTreesOptionOrder() {
        session.toggle("symptoms", "hairball");
        session.toggle("symptoms", "vomit");
        assertEquals(Arrays.asList("vomit", "hairball"), session.getAnswers().get("symptoms"));
    }

    @Test
    public void answersToFollowUpsThatAreNoLongerActiveAreLeftOutButComeBackIfReselected() {
        session.toggle("appetite", "less");
        session.answerFollowUp("vomiting", "once");
        session.toggle("appetite", "less"); // deselect: vomiting no longer active
        assertTrue(session.getAnswers().isEmpty());

        session.toggle("appetite", "less"); // reselect
        assertEquals(Collections.singletonList("once"), session.getAnswers().get("vomiting"));
    }

    @Test
    public void restoreRebuildsTheSameState() {
        session.toggle("appetite", "less");
        session.toggle("water", "more");
        session.toggle("symptoms", "vomit");
        session.answerFollowUp("hiding", "yes");
        session.answerFollowUp("urination", "no");
        Map<String, List<String>> saved = session.getAnswers();

        CheckInSession rebuilt = new CheckInSession(tree);
        rebuilt.restore(saved);
        assertEquals(saved, rebuilt.getAnswers());
        assertEquals(ids(session.getActiveFollowUps()), ids(rebuilt.getActiveFollowUps()));
        assertTrue(rebuilt.isSelected("appetite", "less"));
        assertEquals("yes", rebuilt.getFollowUpAnswer("hiding"));
    }

    @Test
    public void restoreIgnoresIdsAndValuesThatNoLongerExist() {
        Map<String, List<String>> saved = new LinkedHashMap<>();
        saved.put("appetite", Collections.singletonList("less"));
        saved.put("removed_group", Collections.singletonList("x"));
        saved.put("water", Collections.singletonList("removed_value"));
        session.restore(saved);

        assertTrue(session.isSelected("appetite", "less"));
        assertEquals(Collections.singletonList("less"), session.getAnswers().get("appetite"));
        assertFalse(session.getAnswers().containsKey("water"));
    }

    @Test
    public void restoreReplacesWhateverWasThereBefore() {
        session.toggle("appetite", "more");
        session.restore(Collections.<String, List<String>>emptyMap());
        assertFalse(session.hasAnySelection());
    }

    @Test
    public void changingTheReturnedAnswersDoesNotChangeTheSession() {
        session.toggle("symptoms", "vomit");
        Map<String, List<String>> answers = session.getAnswers();
        answers.get("symptoms").add("hairball");
        answers.put("appetite", Collections.singletonList("more"));

        assertEquals(Collections.singletonList("vomit"), session.getAnswers().get("symptoms"));
        assertFalse(session.getAnswers().containsKey("appetite"));
    }
}
