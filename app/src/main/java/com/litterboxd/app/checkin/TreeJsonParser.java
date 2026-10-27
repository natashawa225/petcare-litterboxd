package com.litterboxd.app.checkin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the check-in JSON (res/raw/checkin_tree.json) into a DecisionTree.
 *
 * Expected shape:
 * {
 *   "categories": [
 *     { "id": "appetite_water", "titleKey": "checkin_category_appetite_water",
 *       "groups": [
 *         { "id": "appetite", "titleKey": "checkin_group_appetite", "selection": "single",
 *           "chips": [ { "value": "less", "labelKey": "chip_appetite_less", "followUps": ["vomiting"] },
 *                      { "value": "normal", "labelKey": "chip_appetite_normal", "normal": true } ] } ] } ],
 *   "followUps": [
 *     { "id": "vomiting", "textKey": "q_vomiting",
 *       "options": [ { "value": "no", "labelKey": "btn_no" }, { "value": "yes", "labelKey": "btn_yes" } ] } ]
 * }
 * Every "...Key" is the NAME of a string resource, so all wording stays in strings.xml.
 * "selection" ("single" or "multi"), "followUps" and "normal" are optional.
 */
public final class TreeJsonParser {

    private TreeJsonParser() {}

    /**
     * @throws JSONException            if the JSON is malformed or a required field is missing
     * @throws IllegalArgumentException if the tree is not valid (see DecisionTree)
     */
    public static DecisionTree parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);

        List<CheckInCategory> categories = new ArrayList<>();
        JSONArray categoryArray = root.getJSONArray("categories");
        for (int i = 0; i < categoryArray.length(); i++) {
            JSONObject c = categoryArray.getJSONObject(i);
            List<Question> groups = new ArrayList<>();
            JSONArray groupArray = c.getJSONArray("groups");
            for (int j = 0; j < groupArray.length(); j++) {
                JSONObject g = groupArray.getJSONObject(j);
                groups.add(new Question(
                        g.getString("id"),
                        g.getString("titleKey"),
                        readOptions(g.getJSONArray("chips")),
                        readMode(g.optString("selection", "single"))));
            }
            categories.add(new CheckInCategory(c.getString("id"), c.getString("titleKey"), groups));
        }

        List<Question> followUps = new ArrayList<>();
        JSONArray followUpArray = root.getJSONArray("followUps");
        for (int i = 0; i < followUpArray.length(); i++) {
            JSONObject q = followUpArray.getJSONObject(i);
            followUps.add(new Question(
                    q.getString("id"), q.getString("textKey"), readOptions(q.getJSONArray("options"))));
        }
        return new DecisionTree(categories, followUps);
    }

    private static List<AnswerOption> readOptions(JSONArray array) throws JSONException {
        List<AnswerOption> options = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            JSONArray followUps = o.optJSONArray("followUps");
            options.add(new AnswerOption(
                    o.getString("value"),
                    o.getString("labelKey"),
                    followUps == null ? null : readStringList(followUps),
                    o.optBoolean("normal", false)));
        }
        return options;
    }

    private static SelectionMode readMode(String selection) {
        if ("single".equals(selection)) {
            return SelectionMode.SINGLE;
        }
        if ("multi".equals(selection)) {
            return SelectionMode.MULTI;
        }
        throw new IllegalArgumentException("Unknown selection mode '" + selection + "'");
    }

    private static List<String> readStringList(JSONArray array) throws JSONException {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }
}
