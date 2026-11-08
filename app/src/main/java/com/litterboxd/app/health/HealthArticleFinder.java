package com.litterboxd.app.health;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a site-search page and an article page into a short reading card.
 * Plain Java so the keyword check can be unit-tested without a phone.
 */
public final class HealthArticleFinder {

    public static final String SOURCE_ICATCARE = "International Cat Care";
    public static final String ICATCARE_ORIGIN = "https://icatcare.org";

    private static final int QUICK_READ_CHARS = 280;
    private static final Pattern SEARCH_HIT = Pattern.compile(
            "href=\"(/articles/[^\"]+)\" class=\"Prototype-link\">\\s*"
                    + "<h6 class=\"Prototype-item-name\">\\s*([^<]*?)\\s*</h6>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTRO = Pattern.compile(
            "id=\"intro\"[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PARAGRAPH = Pattern.compile(
            "<p[^>]*>(.*?)</p>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
            "<(script|style)[^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern HEX_ENTITY = Pattern.compile("&#x([0-9a-fA-F]+);");
    private static final Pattern DEC_ENTITY = Pattern.compile("&#(\\d+);");

    private HealthArticleFinder() {}

    public static final class Hit {
        public final String title;
        public final String path;

        public Hit(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }

    /** Article links from an International Cat Care search results page, first occurrence of each path. */
    public static List<Hit> parseSearch(String html) {
        List<Hit> hits = new ArrayList<>();
        if (html == null || html.isEmpty()) return hits;
        Map<String, Hit> unique = new LinkedHashMap<>();
        Matcher matcher = SEARCH_HIT.matcher(html);
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String title = clean(matcher.group(2));
            if (path.isEmpty() || title.isEmpty() || unique.containsKey(path)) continue;
            unique.put(path, new Hit(title, path));
        }
        hits.addAll(unique.values());
        return hits;
    }

    /**
     * The site search returns most of its articles, so a page that only mentions a sign in passing
     * can come first. Pages named after the signs are used when there are any, best match first.
     */
    public static List<Hit> bestCandidates(List<Hit> hits, List<String> terms) {
        List<Hit> named = new ArrayList<>();
        for (Hit hit : hits) {
            if (termsInTitle(hit, terms) > 0) named.add(hit);
        }
        if (named.isEmpty()) return hits;
        named.sort(Comparator.comparingInt((Hit hit) -> termsInTitle(hit, terms)).reversed());
        return named;
    }

    private static int termsInTitle(Hit hit, List<String> terms) {
        String title = hit.title.toLowerCase(Locale.US);
        int count = 0;
        for (String term : terms) {
            if (hasWordStartingWith(title, term)) count++;
        }
        return count;
    }

    /** True when the article itself (not the site menus) mentions every sign that fired the alert. */
    public static boolean pageMatches(String html, List<String> terms) {
        if (terms == null || terms.isEmpty()) return false;
        String text = visibleText(articleBody(html));
        for (String term : terms) {
            if (term == null || term.trim().isEmpty()) return false;
            if (!hasWordStartingWith(text, term)) return false;
        }
        return true;
    }

    /** Whole-word prefix, so "strain" finds "straining" but not "restraint", and "urin" not "during". */
    private static boolean hasWordStartingWith(String lowerCaseText, String term) {
        String prefix = term.trim().toLowerCase(Locale.US);
        int at = lowerCaseText.indexOf(prefix);
        while (at >= 0) {
            if (at == 0 || !Character.isLetterOrDigit(lowerCaseText.charAt(at - 1))) return true;
            at = lowerCaseText.indexOf(prefix, at + 1);
        }
        return false;
    }

    private static String articleBody(String html) {
        if (html == null) return "";
        int at = indexOfIgnoreCase(html, "Article-content");
        return at >= 0 ? html.substring(at) : html;
    }

    /**
     * The opening paragraph, shortened to a couple of sentences so it can be read without
     * opening the website.
     */
    public static String openingSummary(String html) {
        if (html == null) return "";
        String chunk = null;
        Matcher intro = INTRO.matcher(html);
        if (intro.find()) {
            Matcher paragraph = PARAGRAPH.matcher(intro.group(1));
            if (paragraph.find()) chunk = paragraph.group(1);
        }
        if (chunk == null) {
            Matcher paragraph = PARAGRAPH.matcher(articleBody(html));
            while (paragraph.find()) {
                String candidate = clean(paragraph.group(1));
                if (candidate.length() >= 80 && !candidate.toLowerCase(Locale.US).startsWith("reading time")) {
                    chunk = paragraph.group(1);
                    break;
                }
            }
        }
        return quickRead(clean(chunk));
    }

    public static String articleUrl(String path) {
        if (path == null) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (!path.startsWith("/")) path = "/" + path;
        return ICATCARE_ORIGIN + path;
    }

    static String visibleText(String html) {
        if (html == null) return "";
        String stripped = SCRIPT_OR_STYLE.matcher(html).replaceAll(" ");
        return clean(stripped).toLowerCase(Locale.US);
    }

    static String clean(String raw) {
        if (raw == null) return "";
        String text = TAG.matcher(raw).replaceAll(" ");
        text = decodeEntities(text);
        text = text.replace('\u00a0', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }

    static String quickRead(String text) {
        if (text == null) return "";
        text = text.trim();
        if (text.length() <= QUICK_READ_CHARS) return text;
        int end = text.lastIndexOf(". ", QUICK_READ_CHARS);
        if (end < 80) end = text.lastIndexOf(' ', QUICK_READ_CHARS);
        if (end < 80) end = QUICK_READ_CHARS;
        String cut = text.substring(0, end).trim();
        if (!cut.endsWith(".") && !cut.endsWith("!") && !cut.endsWith("?")) {
            cut = cut + ".";
        }
        return cut;
    }

    private static String decodeEntities(String text) {
        text = replaceNumeric(HEX_ENTITY, text, 16);
        text = replaceNumeric(DEC_ENTITY, text, 10);
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&ndash;", "-")
                .replace("&mdash;", "-");
    }

    private static String replaceNumeric(Pattern pattern, String text, int radix) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1), radix);
            String ch = code >= 0 && code <= 0x10FFFF
                    ? new String(Character.toChars(code))
                    : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(ch));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.US).indexOf(needle.toLowerCase(Locale.US));
    }
}
