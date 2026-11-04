package com.litterboxd.app.ui.health;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.health.HealthArticle;
import com.litterboxd.app.health.HealthContent;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.HealthRuleEvaluator;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.util.AppExecutors;

import java.util.Collections;
import java.util.List;

/**
 * Preset articles for one fired alert: short summary in-app, full detail via Read more (browser).
 */
public class HealthArticlesActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_RULE_ID = "rule_id";

    private TextView tvIntro;
    private LinearLayout layoutArticles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_article);

        tvIntro = findViewById(R.id.tvArticlesIntro);
        layoutArticles = findViewById(R.id.layoutArticles);

        final long catId = getIntent().getLongExtra(EXTRA_CAT_ID, new AppPrefs(this).getSelectedCatId());
        final String ruleId = getIntent().getStringExtra(EXTRA_RULE_ID);
        if (ruleId == null || ruleId.isEmpty()) {
            Toast.makeText(this, R.string.health_alerts_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(this);
            Cat cat = new CatDao(db).getCatById(catId);
            HealthContent content = HealthContent.load(this);
            List<HealthArticle> articles = Collections.emptyList();
            String catName = cat != null ? cat.getName() : getString(R.string.default_owner_name);
            String titleKey = null;
            if (content != null) {
                // The reading list is fixed per rule, so no need to re-run the evaluator.
                for (HealthRule rule : content.getRules()) {
                    if (ruleId.equals(rule.getId())) {
                        titleKey = rule.getTitleKey();
                        articles = HealthRuleEvaluator.articlesFor(rule, content.getArticlesById());
                        break;
                    }
                }
            }

            final List<HealthArticle> finalArticles = articles;
            final String finalCatName = catName;
            final String finalTitleKey = titleKey;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalArticles.isEmpty()) {
                    Toast.makeText(this, R.string.health_articles_empty, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (finalTitleKey != null) {
                    tvIntro.setText(getString(R.string.health_articles_intro,
                            CheckInContent.text(this, finalTitleKey, finalCatName)));
                } else {
                    tvIntro.setText(R.string.health_articles_intro_generic);
                }
                renderArticles(finalArticles);
            });
        });
    }

    private void renderArticles(List<HealthArticle> articles) {
        layoutArticles.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (HealthArticle article : articles) {
            View row = inflater.inflate(R.layout.item_health_article, layoutArticles, false);
            ((TextView) row.findViewById(R.id.tvArticleTitle))
                    .setText(CheckInContent.text(this, article.getTitleKey()));
            ((TextView) row.findViewById(R.id.tvArticleSource))
                    .setText(CheckInContent.text(this, article.getSourceKey()));
            ((TextView) row.findViewById(R.id.tvArticleSummary))
                    .setText(CheckInContent.text(this, article.getSummaryKey()));
            MaterialButton readMore = row.findViewById(R.id.btnReadMore);
            readMore.setOnClickListener(v -> openUrl(article.getUrl()));
            layoutArticles.addView(row);
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.health_article_open_failed, Toast.LENGTH_LONG).show();
        }
    }
}
