package com.litterboxd.app;

import com.litterboxd.app.checkin.CheckInCategory;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.checkin.SelectionMode;
import com.litterboxd.app.checkin.TreeJsonParser;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeJsonParserTest {

    private static final String VALID = "{"
            + "\"categories\":[{\"id\":\"c\",\"titleKey\":\"t_c\",\"groups\":["
            + "  {\"id\":\"a\",\"titleKey\":\"t_a\",\"selection\":\"multi\",\"chips\":["
            + "    {\"value\":\"x\",\"labelKey\":\"l_x\",\"followUps\":[\"f\"]},"
            + "    {\"value\":\"n\",\"labelKey\":\"l_n\",\"normal\":true}]},"
            + "  {\"id\":\"b\",\"titleKey\":\"t_b\",\"chips\":["
            + "    {\"value\":\"p\",\"labelKey\":\"l_p\"},{\"value\":\"q\",\"labelKey\":\"l_q\"}]}]}],"
            + "\"followUps\":[{\"id\":\"f\",\"textKey\":\"t_f\",\"options\":["
            + "  {\"value\":\"yes\",\"labelKey\":\"l_yes\"},{\"value\":\"no\",\"labelKey\":\"l_no\"}]}]}";

    @Test
    public void parsesCategoriesGroupsChipsAndFollowUps() throws JSONException {
        DecisionTree tree = TreeJsonParser.parse(VALID);

        assertEquals(1, tree.getCategories().size());
        CheckInCategory category = tree.getCategories().get(0);
        assertEquals("c", category.getId());
        assertEquals("t_c", category.getTitleKey());
        assertEquals(2, category.getGroups().size());

        Question a = tree.getQuestion("a");
        assertEquals(SelectionMode.MULTI, a.getMode());
        assertEquals(2, a.getOptions().size());
        assertEquals("l_x", a.getOption("x").getLabelKey());
        assertEquals("f", a.getOption("x").getFollowUpIds().get(0));
        assertTrue(a.getOption("n").isNormal());
        assertFalse(a.getOption("x").isNormal());

        assertEquals("single selection is the default", SelectionMode.SINGLE, tree.getQuestion("b").getMode());
        assertEquals("t_f", tree.getQuestion("f").getTextKey());
    }

    @Test(expected = JSONException.class)
    public void malformedJsonIsRejected() throws JSONException {
        TreeJsonParser.parse("{ not json");
    }

    @Test(expected = JSONException.class)
    public void missingRequiredFieldIsRejected() throws JSONException {
        TreeJsonParser.parse("{\"categories\":[]}"); // no "followUps"
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownSelectionModeIsRejected() throws JSONException {
        TreeJsonParser.parse(VALID.replace("\"multi\"", "\"several\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aValidShapeButBrokenTreeIsRejected() throws JSONException {
        // the follow-up "f" is no longer named by any chip
        TreeJsonParser.parse(VALID.replace("\"followUps\":[\"f\"]", "\"followUps\":[]"));
    }
}
