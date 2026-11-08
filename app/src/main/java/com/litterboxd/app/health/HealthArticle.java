package com.litterboxd.app.health;

/** The real page an alert's possible reasons come from, shown as its citation. */
public final class HealthArticle {
    private final String id;
    private final String titleKey;
    private final String sourceKey;
    private final String url;

    public HealthArticle(String id, String titleKey, String sourceKey, String url) {
        this.id = id;
        this.titleKey = titleKey;
        this.sourceKey = sourceKey;
        this.url = url;
    }

    public String getId() { return id; }
    public String getTitleKey() { return titleKey; }
    public String getSourceKey() { return sourceKey; }
    public String getUrl() { return url; }
}
