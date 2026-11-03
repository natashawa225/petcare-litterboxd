package com.litterboxd.app.health;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A rule that currently matches this cat's recent logs. */
public final class HealthAlert {
    private final HealthRule rule;
    private final List<HealthArticle> articles;

    public HealthAlert(HealthRule rule, List<HealthArticle> articles) {
        this.rule = rule;
        this.articles = Collections.unmodifiableList(new ArrayList<>(articles));
    }

    public HealthRule getRule() { return rule; }
    public List<HealthArticle> getArticles() { return articles; }
}
