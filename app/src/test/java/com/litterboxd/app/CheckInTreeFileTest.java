package com.litterboxd.app;

import com.litterboxd.app.checkin.AnswerOption;
import com.litterboxd.app.checkin.CheckInCategory;
import com.litterboxd.app.checkin.CheckInSession;
import com.litterboxd.app.checkin.DecisionTree;
import com.litterboxd.app.checkin.Question;
import com.litterboxd.app.checkin.TreeJsonParser;

import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the REAL check-in files (res/raw/checkin_tree.json and res/values/strings.xml).
 * Gradle runs unit tests with the module folder (app/) as the working directory.
 */
public class CheckInTreeFileTest {

    private static final String TREE_PATH = "src/main/res/raw/checkin_tree.json";
    private static final String STRINGS_PATH = "src/main/res/values/strings.xml";

    /** The second screen should stay short (worst case: ate less + drank more + more litter visits). */
    private static final int MAX_FOLLOW_UPS = 4;

    private static DecisionTree tree;
    private static Set<String> stringNames;

    @BeforeClass
    public static void loadFiles() throws IOException, JSONException {
        String json = new String(Files.readAllBytes(Paths.get(TREE_PATH)), StandardCharsets.UTF_8);
        tree = TreeJsonParser.parse(json);

        String strings = new String(Files.readAllBytes(Paths.get(STRINGS_PATH)), StandardCharsets.UTF_8);
        stringNames = new HashSet<>();
        Matcher m = Pattern.compile("<string name=\"([^\"]+)\"").matcher(strings);
        while (m.find()) {
            stringNames.add(m.group(1));
        }
    }

    private static List<String> ids(List<Question> questions) {
        List<String> ids = new ArrayList<>();
        for (Question q : questions) {
            ids.add(q.getId());
        }
        return ids;
    }

    @Test
    public void treeFileIsValidAndHasTheFiveDailyGroups() {
        List<String> categoryIds = new ArrayList<>();
        for (CheckInCategory category : tree.getCategories()) {
            categoryIds.add(category.getId());
        }
        assertEquals(Arrays.asList("appetite_water", "litter", "energy_mood"), categoryIds);
        assertEquals(Arrays.asList("appetite", "water", "litter", "energy", "mood"), ids(tree.getGroups()));
    }

    @Test
    public void everyKeyInTheTreeExistsInStringsXml() {
        List<String> missing = new ArrayList<>();
        for (CheckInCategory category : tree.getCategories()) {
            if (!stringNames.contains(category.getTitleKey())) {
                missing.add(category.getTitleKey());
            }
        }
        for (Question q : tree.getQuestions()) {
            if (!stringNames.contains(q.getTextKey())) {
                missing.add(q.getTextKey());
            }
            for (AnswerOption option : q.getOptions()) {
                if (!stringNames.contains(option.getLabelKey())) {
                    missing.add(option.getLabelKey());
                }
            }
        }
        assertTrue("Missing from strings.xml: " + missing, missing.isEmpty());
    }

    @Test
    public void allNormalGivesNoFollowUpsAndTheNormalAnswers() {
        CheckInSession s = new CheckInSession(tree);
        s.selectAllNormal();
        assertTrue(s.getActiveFollowUps().isEmpty());

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("appetite", Collections.singletonList("normal"));
        expected.put("water", Collections.singletonList("normal"));
        expected.put("litter", Collections.singletonList("normal"));
        expected.put("energy", Collections.singletonList("normal"));
        expected.put("mood", Collections.singletonList("normal"));
        assertEquals(expected, s.getAnswers());
    }

    @Test
    public void moreLitterVisitsAsksAboutDrinkingMore() {
        CheckInSession s = new CheckInSession(tree);
        s.toggle("litter", "more");
        assertEquals(Collections.singletonList("drinking_more"), ids(s.getActiveFollowUps()));
    }

    @Test
    public void lowEnergyAsksAboutHidingOnlyOnceEvenWithLowAppetite() {
        CheckInSession s = new CheckInSession(tree);
        s.toggle("energy", "low");
        assertEquals(Collections.singletonList("hiding"), ids(s.getActiveFollowUps()));
        s.toggle("appetite", "less");
        assertEquals(Arrays.asList("vomiting", "hiding"), ids(s.getActiveFollowUps()));
    }

    @Test
    public void ateLessAsksVomitingThenHiding() {
        CheckInSession s = new CheckInSession(tree);
        s.toggle("appetite", "less");
        assertEquals(Arrays.asList("vomiting", "hiding"), ids(s.getActiveFollowUps()));
    }

    @Test
    public void drankMoreAsksAboutPeeingMore() {
        CheckInSession s = new CheckInSession(tree);
        s.toggle("water", "more");
        assertEquals(Collections.singletonList("urination_more"), ids(s.getActiveFollowUps()));
    }

    @Test
    public void everyChipCombinationStaysWithinTheFollowUpLimit() {
        List<Question> groups = tree.getGroups();
        int expected = 1;
        for (Question group : groups) {
            expected *= group.getOptions().size() + 1; // each chip, or nothing chosen
        }
        int combinations = explore(groups, 0, new ArrayList<String[]>());
        assertEquals("every combination should be checked", expected, combinations);
    }

    /** Tries every chip (or none) in every group. Returns how many combinations were checked. */
    private int explore(List<Question> groups, int index, List<String[]> chosen) {
        if (index == groups.size()) {
            CheckInSession s = new CheckInSession(tree);
            for (String[] pick : chosen) {
                if (pick[1] != null) {
                    s.toggle(pick[0], pick[1]);
                }
            }
            assertTrue("Too many follow-ups for " + s.getAnswers() + ": " + ids(s.getActiveFollowUps()),
                    s.getActiveFollowUps().size() <= MAX_FOLLOW_UPS);
            return 1;
        }
        int checked = 0;
        Question group = groups.get(index);

        chosen.add(new String[]{group.getId(), null});
        checked += explore(groups, index + 1, chosen);
        chosen.remove(chosen.size() - 1);

        for (AnswerOption option : group.getOptions()) {
            chosen.add(new String[]{group.getId(), option.getValue()});
            checked += explore(groups, index + 1, chosen);
            chosen.remove(chosen.size() - 1);
        }
        return checked;
    }
}
