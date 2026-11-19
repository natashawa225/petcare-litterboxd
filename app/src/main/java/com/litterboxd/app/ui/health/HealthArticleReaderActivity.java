package com.litterboxd.app.ui.health;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.litterboxd.app.R;

/**
 * Shows a health article inside the app. The site's address is shown under the title. Links on
 * the same site stay in the reader; links to other sites open in the phone's browser (with its own
 * address bar), and anything that isn't a web page is ignored.
 */
public class HealthArticleReaderActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_URL = "url";

    private WebView webView;
    private ProgressBar progress;
    private View layoutError;
    private String failedUrl;
    private TextView siteView;
    /** The source's host without "www."; only pages on this site open in the reader. */
    private String sourceHost;

    public static void start(Context context, String title, String url) {
        Intent intent = new Intent(context, HealthArticleReaderActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_URL, url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_article_reader);

        webView = findViewById(R.id.webArticle);
        progress = findViewById(R.id.progressArticle);
        layoutError = findViewById(R.id.layoutArticleError);
        TextView title = findViewById(R.id.tvReaderTitle);
        MaterialButton btnClose = findViewById(R.id.btnReaderClose);
        MaterialButton btnRetry = findViewById(R.id.btnReaderRetry);

        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        siteView = findViewById(R.id.tvReaderSite);
        btnClose.setOnClickListener(v -> finish());
        btnRetry.setOnClickListener(v -> {
            showPage();
            webView.loadUrl(failedUrl);
        });

        setUpWebView();
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (isWebPage(url)) {
            sourceHost = bareHost(url);
            showSite(url);
            webView.loadUrl(url);
        } else {
            finish();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        WebSettings settings = webView.getSettings();
        // The article sites need scripts to lay out; no app objects are exposed to the page.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String next = request.getUrl().toString();
                if (!isWebPage(next)) return true;            // mailto:, intent:, ... ignored
                if (isOnSourceSite(next)) return false;       // stay in the reader
                if (request.isRedirect()) return false;      // the site's own redirect: follow it
                // Any other site opens in the phone's browser, with its real address bar, so a
                // page can never pass itself off as part of Litterboxd.
                if (request.hasGesture()) openInBrowser(next);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                showSite(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    failedUrl = request.getUrl().toString();
                    webView.setVisibility(View.INVISIBLE);
                    layoutError.setVisibility(View.VISIBLE);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }
        });
    }

    private void showPage() {
        layoutError.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void showSite(String url) {
        String host = Uri.parse(url).getHost();
        siteView.setText(host != null ? host : "");
    }

    private boolean isOnSourceSite(String url) {
        String host = bareHost(url);
        return sourceHost != null && host != null
                && (host.equals(sourceHost) || host.endsWith("." + sourceHost));
    }

    /** "https://www.Example.org/a" -> "example.org"; null if there is no host. */
    private static String bareHost(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) return null;
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private void openInBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.health_article_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isWebPage(String url) {
        if (url == null) return false;
        String scheme = Uri.parse(url).getScheme();
        return "https".equals(scheme) || "http".equals(scheme);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            showPage();
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
