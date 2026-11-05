package com.litterboxd.app.ui.health;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;
import com.litterboxd.app.data.dao.CatDao;
import com.litterboxd.app.data.db.LitterboxdDbHelper;
import com.litterboxd.app.data.model.Cat;
import com.litterboxd.app.data.session.AppPrefs;
import com.litterboxd.app.health.HealthArticleClient;
import com.litterboxd.app.health.HealthContent;
import com.litterboxd.app.health.HealthRule;
import com.litterboxd.app.health.SearchedArticle;
import com.litterboxd.app.ui.checkin.CheckInContent;
import com.litterboxd.app.util.AppExecutors;

import java.io.IOException;
import java.util.List;

/**
 * More info for one alert: the alert note is the quick read. Optional pages come from a live
 * search and are kept only when they mention the signs that fired the alert.
 */
public class HealthArticlesActivity extends AppCompatActivity {

    public static final String EXTRA_CAT_ID = "cat_id";
    public static final String EXTRA_RULE_ID = "rule_id";

    private TextView tvSummary;
    private TextView tvIntro;
    private TextView tvStatus;
    private ProgressBar progress;
    private MaterialButton btnRetry;
    private LinearLayout layoutArticles;

    private int requestId = 0;
    private String searchQuery;
    private List<String> searchTerms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_article);

        tvSummary = findViewById(R.id.tvAlertSummary);
        tvIntro = findViewById(R.id.tvArticlesIntro);
        tvStatus = findViewById(R.id.tvSearchStatus);
        progress = findViewById(R.id.progressSearch);
        btnRetry = findViewById(R.id.btnSearchAgain);
        layoutArticles = findViewById(R.id.layoutArticles);

        tvIntro.setText(R.string.health_articles_intro);
        btnRetry.setOnClickListener(v -> startSearch());

        final long catId = getIntent().getLongExtra(EXTRA_CAT_ID, new AppPrefs(this).getSelectedCatId());
        final String ruleId = getIntent().getStringExtra(EXTRA_RULE_ID);
        if (ruleId == null || ruleId.isEmpty()) {
            Toast.makeText(this, R.string.health_alerts_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        showSearching();
        AppExecutors.getInstance().diskIO().execute(() -> {
            LitterboxdDbHelper db = LitterboxdDbHelper.getInstance(this);
            Cat cat = new CatDao(db).getCatById(catId);
            HealthContent content = HealthContent.load(this);
            String catName = cat != null ? cat.getName() : getString(R.string.default_owner_name);
            HealthRule rule = null;
            if (content != null) {
                for (HealthRule candidate : content.getRules()) {
                    if (ruleId.equals(candidate.getId())) {
                        rule = candidate;
                        break;
                    }
                }
            }
            final HealthRule finalRule = rule;
            final String finalCatName = catName;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalRule == null) {
                    Toast.makeText(this, R.string.health_alerts_error, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                tvSummary.setText(CheckInContent.text(this, finalRule.getMessageKey(), finalCatName));
                searchQuery = finalRule.getSearchQuery();
                searchTerms = finalRule.getSearchTerms();
                startSearch();
            });
        });
    }

    private void startSearch() {
        final int id = ++requestId;
        final String query = searchQuery;
        final List<String> terms = searchTerms;
        if (query == null || terms == null) return;
        showSearching();
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<SearchedArticle> articles = null;
            IOException failure = null;
            try {
                articles = new HealthArticleClient().find(query, terms);
            } catch (IOException e) {
                failure = e;
            }
            final List<SearchedArticle> finalArticles = articles;
            final boolean failed = failure != null;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (isFinishing() || isDestroyed() || id != requestId) return;
                progress.setVisibility(View.GONE);
                if (failed) {
                    tvStatus.setText(R.string.health_articles_error);
                    btnRetry.setVisibility(View.VISIBLE);
                    return;
                }
                if (finalArticles == null || finalArticles.isEmpty()) {
                    tvStatus.setText(R.string.health_articles_none);
                    btnRetry.setVisibility(View.VISIBLE);
                    return;
                }
                tvStatus.setVisibility(View.GONE);
                btnRetry.setVisibility(View.GONE);
                renderArticles(finalArticles);
            });
        });
    }

    private void showSearching() {
        layoutArticles.removeAllViews();
        progress.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.health_articles_searching);
        btnRetry.setVisibility(View.GONE);
    }

    private void renderArticles(List<SearchedArticle> articles) {
        layoutArticles.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SearchedArticle article : articles) {
            View row = inflater.inflate(R.layout.item_health_article, layoutArticles, false);
            ((TextView) row.findViewById(R.id.tvArticleTitle)).setText(article.getTitle());
            ((TextView) row.findViewById(R.id.tvArticleSource)).setText(article.getSource());
            TextView summary = row.findViewById(R.id.tvArticleSummary);
            if (article.getSummary() == null || article.getSummary().isEmpty()) {
                summary.setVisibility(View.GONE);
            } else {
                summary.setText(article.getSummary());
            }
            MaterialButton readMore = row.findViewById(R.id.btnReadMore);
            View citation = row.findViewById(R.id.layoutCitation);
            TextView citationLabel = row.findViewById(R.id.tvCitationLabel);
            TextView citationUrl = row.findViewById(R.id.tvCitationUrl);
            MaterialButton openCited = row.findViewById(R.id.btnOpenCited);
            citationLabel.setText(getString(R.string.health_article_cite_label, article.getSource()));
            citationUrl.setText(article.getUrl());
            readMore.setOnClickListener(v -> {
                citation.setVisibility(View.VISIBLE);
                readMore.setVisibility(View.GONE);
            });
            openCited.setOnClickListener(v -> openUrl(article.getUrl()));
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
