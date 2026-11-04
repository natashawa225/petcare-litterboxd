package com.litterboxd.app.health;

/** A preset help article: short in-app summary + link to the full real page (also used as citation). */
public final class HealthArticle {
    private final String id;
    private final String titleKey;
    private final String summaryKey;
    private final String sourceKey;
    private final String url;

    public HealthArticle(String id, String titleKey, String summaryKey, String sourceKey,
                         String url) {
        this.id = id;
        this.titleKey = titleKey;
        this.summaryKey = summaryKey;
        this.sourceKey = sourceKey;
        this.url = url;
    }

    public String getId() { return id; }
    public String getTitleKey() { return titleKey; }
    public String getSummaryKey() { return summaryKey; }
    public String getSourceKey() { return sourceKey; }
    public String getUrl() { return url; }
}
