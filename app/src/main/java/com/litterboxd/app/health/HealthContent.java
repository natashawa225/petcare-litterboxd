package com.litterboxd.app.health;

import android.content.Context;
import android.util.Log;

import com.litterboxd.app.R;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Loads health rules and the articles they cite from res/raw. Further reading is searched when asked. */
public final class HealthContent {

    private static final String TAG = "HealthContent";

    private final Map<String, HealthArticle> articlesById;
    private final List<HealthRule> rules;
    private final List<HealthReading> readings;

    private HealthContent(Map<String, HealthArticle> articlesById, List<HealthRule> rules,
                          List<HealthReading> readings) {
        this.articlesById = articlesById;
        this.rules = rules;
        this.readings = readings;
    }

    public Map<String, HealthArticle> getArticlesById() {
        return articlesById;
    }

    public List<HealthRule> getRules() {
        return rules;
    }

    public List<HealthReading> getReadings() {
        return readings;
    }

    public HealthArticle getArticle(String id) {
        return articlesById.get(id);
    }

    /** Call off the UI thread. Returns null if the rules file is broken. */
    public static HealthContent load(Context context) {
        try {
            String rulesJson = readRaw(context, R.raw.health_rules);
            Map<String, HealthArticle> articles = HealthContentParser.parseArticles(rulesJson);
            List<HealthRule> rules = HealthContentParser.parseRules(rulesJson, articles);
            List<HealthReading> readings = HealthContentParser.parseReadings(rulesJson, articles);
            return new HealthContent(articles, Collections.unmodifiableList(rules),
                    Collections.unmodifiableList(readings));
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Log.e(TAG, "Could not load health content", e);
            return null;
        }
    }

    private static String readRaw(Context context, int resId) throws IOException {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
