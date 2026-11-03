package com.litterboxd.app.health;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A preset help article: short in-app summary + link to the full real page (also used as citation). */
public final class HealthArticle {
    private final String id;
    private final String titleKey;
    private final String summaryKey;
    private final String sourceKey;
    private final String url;
    private final List<String> keywords;

    public HealthArticle(String id, String titleKey, String summaryKey, String sourceKey,
                         String url, List<String> keywords) {
        this.id = id;
        this.titleKey = titleKey;
        this.summaryKey = summaryKey;
        this.sourceKey = sourceKey;
        this.url = url;
        this.keywords = Collections.unmodifiableList(new ArrayList<>(keywords));
    }

    public String getId() { return id; }
    public String getTitleKey() { return titleKey; }
    public String getSummaryKey() { return summaryKey; }
    public String getSourceKey() { return sourceKey; }
    public String getUrl() { return url; }
    public List<String> getKeywords() { return keywords; }
}
