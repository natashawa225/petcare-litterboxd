package com.litterboxd.app.ui.health;

import android.content.Context;

import com.litterboxd.app.R;
import com.litterboxd.app.health.HealthAlert;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.ui.checkin.CheckInContent;

/** The "Source: ..." line and link shared by the alert cards and the urgent popup. */
public final class HealthCitations {

    private HealthCitations() {}

    /** The article cited for this alert. Every rule has one (checked when the rules load). */
    public static HealthArticle sourceOf(HealthAlert alert) {
        return alert.getArticles().get(0);
    }

    public static String sourceLine(Context context, HealthArticle article) {
        return context.getString(R.string.health_alert_source,
                CheckInContent.text(context, article.getTitleKey()),
                CheckInContent.text(context, article.getSourceKey()));
    }

    public static void open(Context context, HealthArticle article) {
        HealthArticleReaderActivity.start(context,
                CheckInContent.text(context, article.getTitleKey()), article.getUrl());
    }
}
