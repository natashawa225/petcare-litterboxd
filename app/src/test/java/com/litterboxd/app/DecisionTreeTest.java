package com.litterboxd.app;

import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.CheckInCategory;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.checkin.SelectionMode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DecisionTreeTest {

    private static AnswerOption opt(String value, String... followUps) {
        return new AnswerOption(value, "label_" + value, Arrays.asList(followUps));
    }

    private static AnswerOption normal(String value) {
        return new AnswerOption(value, "label_" + value, null, true);
    }

    private static Question q(String id, AnswerOption... options) {
        return new Question(id, "text_" + id, Arrays.asList(options));
    }

    private static CheckInCategory category(String id, Question... groups) {
        return new CheckInCategory(id, "title_" + id, Arrays.asList(groups));
    }

    private static List<Question> followUps(Question... questions) {
        return Arrays.asList(questions);
    }

    @Test
    public void validTreeIsAccepted() {
        DecisionTree tree = new DecisionTree(
                Arrays.asList(category("c", q("a", opt("x", "f"), normal("n")))),
                followUps(q("f", opt("yes"), opt("no"))));
        assertEquals(1, tree.getCategories().size());
        assertEquals(1, tree.getGroups().size());
        assertEquals(2, tree.getQuestions().size());
        assertTrue(tree.isGroup("a"));
        assertTrue(!tree.isGroup("f"));
        assertNotNull(tree.getQuestion("f"));
        assertNull(tree.getQuestion("missing"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void noCategoriesIsRejected() {
        new DecisionTree(Collections.<CheckInCategory>emptyList(), Collections.<Question>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void categoryWithoutGroupsIsRejected() {
        category("c");
    }

    @Test(expected = IllegalArgumentException.class)
    public void chipPointingAtMissingFollowUpIsRejected() {
        new DecisionTree(Arrays.asList(category("c", q("a", opt("x", "ghost"), opt("y")))),
                Collections.<Question>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void chipPointingAtAnotherGroupIsRejected() {
        new DecisionTree(
                Arrays.asList(category("c", q("a", opt("x", "b"), opt("y")), q("b", opt("x"), opt("y")))),
                Collections.<Question>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdAcrossGroupAndFollowUpIsRejected() {
        new DecisionTree(Arrays.asList(category("c", q("a", opt("x", "a"), opt("y")))),
                followUps(q("a", opt("x"), opt("y"))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateGroupIdIsRejected() {
        new DecisionTree(
                Arrays.asList(category("c", q("a", opt("x"), opt("y"))), category("d", q("a", opt("x"), opt("y")))),
                Collections.<Question>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unreachableFollowUpIsRejected() {
        new DecisionTree(Arrays.asList(category("c", q("a", opt("x"), opt("y")))),
                followUps(q("orphan", opt("x"), opt("y"))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void followUpLoopIsRejected() {
        // f1 -> f2 -> f1
        new DecisionTree(Arrays.asList(category("c", q("a", opt("x", "f1"), opt("y")))),
                followUps(q("f1", opt("x", "f2"), opt("y")), q("f2", opt("x", "f1"), opt("y"))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void followUpPointingAtItselfIsRejected() {
        new DecisionTree(Arrays.asList(category("c", q("a", opt("x", "f"), opt("y")))),
                followUps(q("f", opt("x", "f"), opt("y"))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void multiSelectFollowUpIsRejected() {
        Question multiFollowUp = new Question("f", "text_f", Arrays.asList(opt("x"), opt("y")), SelectionMode.MULTI);
        new DecisionTree(Arrays.asList(category("c", q("a", opt("x", "f"), opt("y")))), followUps(multiFollowUp));
    }

    @Test(expected = IllegalArgumentException.class)
    public void questionWithOneOptionIsRejected() {
        q("a", opt("only"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateOptionValueIsRejected() {
        q("a", opt("x"), opt("x"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void twoNormalOptionsInOneQuestionIsRejected() {
        q("a", normal("n1"), normal("n2"));
    }
}
