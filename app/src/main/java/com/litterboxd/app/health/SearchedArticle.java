package com.litterboxd.app.health;

/** One page found by a live search: a short opening summary, with the full site as an optional link. */
public final class SearchedArticle {
    private final String title;
    private final String source;
    private final String summary;
    private final String url;

    public SearchedArticle(String title, String source, String summary, String url) {
        this.title = title;
        this.source = source;
        this.summary = summary;
        this.url = url;
    }

    public String getTitle() { return title; }
    public String getSource() { return source; }
    public String getSummary() { return summary; }
    public String getUrl() { return url; }
}
