package com.litterboxd.app.health;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One card in the Daily "Reading Corner": a label chip ("For you" / "Cat care"), a title, the
 * source and reading time, and a thumbnail. The thumbnail is the article's own teaser image
 * (its og:image, looked up once and stored in the seed data, loaded with Glide); articles without
 * one, or offline, show a placeholder emoji on a soft tint.
 * Plain Java so the parsing is unit-tested.
 */
public final class ReadingCard {

    /** Picked from today's check-in answers (see HealthRuleEvaluator.readingsFor). */
    public static final String KIND_FOR_YOU = "for_you";
    /** General cat-care reading from res/raw/cat_care_articles.json. */
    public static final String KIND_CAT_CARE = "cat_care";

    private final String kind;
    private final String title;
    private final String source;
    private final int minutes;      // 0 = unknown, not shown
    private final String emoji;
    private final String tint;      // "oat" | "sage" | "clay" | "blush"
    private final String url;
    private final String imageUrl;  // "" = no teaser image, the emoji tile is shown

    public ReadingCard(String kind, String title, String source, int minutes, String emoji,
                       String tint, String url, String imageUrl) {
        this.kind = kind;
        this.title = title;
        this.source = source;
        this.minutes = minutes;
        this.emoji = emoji;
        this.tint = tint;
        this.url = url;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getSource() { return source; }
    public int getMinutes() { return minutes; }
    public String getEmoji() { return emoji; }
    public String getTint() { return tint; }
    public String getUrl() { return url; }
    public String getImageUrl() { return imageUrl; }
    public boolean hasImage() { return !imageUrl.isEmpty(); }
    public boolean isForYou() { return KIND_FOR_YOU.equals(kind); }

    /** Parses cat_care_articles.json. Articles without an https link are skipped. */
    public static List<ReadingCard> parseCatCare(String json) throws JSONException {
        JSONArray array = new JSONObject(json).getJSONArray("articles");
        List<ReadingCard> cards = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String url = o.getString("url");
            if (!url.startsWith("https://")) continue;
            cards.add(new ReadingCard(KIND_CAT_CARE,
                    o.getString("title"),
                    o.getString("source"),
                    o.optInt("minutes", 0),
                    o.optString("emoji", "🐾"),
                    o.optString("tint", "oat"),
                    url,
                    httpsOrEmpty(o.optString("image", ""))));
        }
        return cards;
    }

    /** Thumbnail emoji for a log-based reading, by the article it points to. */
    public static String emojiForReading(String articleId) {
        if (articleId == null) return "🐾";
        switch (articleId) {
            case "source_inappetence": return "🍽️";
            case "source_hydration": return "💧";
            case "source_constipation": return "🧺";
            case "source_lethargy": return "💤";
            case "source_stress": return "🌧️";
            default: return "🐾";
        }
    }

    private static String httpsOrEmpty(String url) {
        return url != null && url.startsWith("https://") ? url : "";
    }
}
