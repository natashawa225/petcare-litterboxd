package com.litterboxd.app.health;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Searches International Cat Care for pages that mention the signs behind an alert.
 * One search request, then a few article pages so each card can show that page's own opening lines.
 */
public final class HealthArticleClient {

    static final int MAX_RESULTS = 3;
    static final int MAX_CANDIDATES = 6;
    private static final int MAX_BYTES = 300_000;
    private static final int TIMEOUT_MS = 8000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public List<SearchedArticle> find(String query, List<String> terms) throws IOException {
        if (query == null || query.trim().isEmpty() || terms == null || terms.isEmpty()) {
            return new ArrayList<>();
        }
        String searchHtml = get(searchUrl(query.trim()));
        List<HealthArticleFinder.Hit> hits = HealthArticleFinder.bestCandidates(
                HealthArticleFinder.parseSearch(searchHtml), terms);
        if (hits.isEmpty()) return new ArrayList<>();
        int count = Math.min(MAX_CANDIDATES, hits.size());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, count));
        try {
            List<Future<SearchedArticle>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final HealthArticleFinder.Hit hit = hits.get(i);
                futures.add(pool.submit(new Callable<SearchedArticle>() {
                    @Override
                    public SearchedArticle call() {
                        return loadIfMatch(hit, terms);
                    }
                }));
            }
            List<SearchedArticle> matched = new ArrayList<>();
            for (Future<SearchedArticle> future : futures) {
                SearchedArticle article = await(future);
                if (article != null) matched.add(article);
                if (matched.size() == MAX_RESULTS) break;
            }
            return matched;
        } finally {
            pool.shutdownNow();
        }
    }

    private static SearchedArticle loadIfMatch(HealthArticleFinder.Hit hit, List<String> terms) {
        try {
            String page = get(HealthArticleFinder.articleUrl(hit.path));
            if (!HealthArticleFinder.pageMatches(page, terms)) return null;
            String summary = HealthArticleFinder.openingSummary(page);
            return new SearchedArticle(
                    hit.title,
                    HealthArticleFinder.SOURCE_ICATCARE,
                    summary,
                    HealthArticleFinder.articleUrl(hit.path));
        } catch (IOException e) {
            return null;
        }
    }

    private static SearchedArticle await(Future<SearchedArticle> future) {
        try {
            return future.get(TIMEOUT_MS + 2000L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return null;
        }
    }

    static String searchUrl(String query) throws IOException {
        return HealthArticleFinder.ICATCARE_ORIGIN + "/search/all?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            String host = connection.getURL().getHost();
            if (host == null
                    || !(host.equals("icatcare.org") || host.endsWith(".icatcare.org"))) {
                throw new IOException("Unexpected host");
            }
            try (InputStream in = connection.getInputStream()) {
                return readLimited(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1 && total < MAX_BYTES) {
            int take = Math.min(read, MAX_BYTES - total);
            out.write(buffer, 0, take);
            total += take;
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
