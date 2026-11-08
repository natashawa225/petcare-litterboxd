package com.litterboxd.app;

import com.litterboxd.app.health.HealthArticleFinder;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HealthArticleFinderTest {

    private static final String SEARCH_HTML =
            "<li class=\"Prototype-item\">\n"
                    + "  <a href=\"/articles/urethral-obstruction-in-cats\" class=\"Prototype-link\">\n"
                    + "    <h6 class=\"Prototype-item-name\">\n"
                    + "      Urethral obstruction in cats\n"
                    + "    </h6>\n"
                    + "  </a>\n"
                    + "</li>\n"
                    + "<li class=\"Prototype-item\">\n"
                    + "  <a href=\"/articles/soiling-indoors\" class=\"Prototype-link\">\n"
                    + "    <h6 class=\"Prototype-item-name\">Soiling indoors</h6>\n"
                    + "  </a>\n"
                    + "</li>\n"
                    + "<a href=\"/articles/urethral-obstruction-in-cats\" class=\"Prototype-link\">\n"
                    + "  <h6 class=\"Prototype-item-name\">Urethral obstruction in cats</h6>\n"
                    + "</a>";

    private static final String MATCHING_ARTICLE =
            "<div class=\"Article-content\">"
                    + "<p class=\"Detail\"><span class=\"Detail-label\">Reading time</span> 4 Minutes</p>"
                    + "<div id=\"intro\"><p>Urethral obstruction, otherwise known as a blocked bladder, "
                    + "is life-threatening. Urgent veterinary treatment is needed so urine can pass. "
                    + "This extra sentence should be cut once the opening is long enough to pass the "
                    + "quick-read limit and keep going with more detail that an owner does not need "
                    + "on the card itself before they decide whether to open the website.</p></div>"
                    + "<p>Male cats may be straining and producing little urine.</p>"
                    + "</div>";

    private static final String MISSING_STRAIN =
            "<div id=\"intro\"><p>Some cats drink more water than usual.</p></div>"
                    + "<p>Urine output can change for many reasons.</p>";

    @Test
    public void parseSearchKeepsFirstArticleLinksInOrder() {
        List<HealthArticleFinder.Hit> hits = HealthArticleFinder.parseSearch(SEARCH_HTML);
        assertEquals(2, hits.size());
        assertEquals("Urethral obstruction in cats", hits.get(0).title);
        assertEquals("/articles/urethral-obstruction-in-cats", hits.get(0).path);
        assertEquals("Soiling indoors", hits.get(1).title);
        assertEquals("https://icatcare.org/articles/urethral-obstruction-in-cats",
                HealthArticleFinder.articleUrl(hits.get(0).path));
    }

    @Test
    public void pageMustContainEverySignFromTheAlert() {
        assertTrue(HealthArticleFinder.pageMatches(MATCHING_ARTICLE, Arrays.asList("strain", "urin")));
        assertFalse(HealthArticleFinder.pageMatches(MISSING_STRAIN, Arrays.asList("strain", "urin")));
        assertFalse(HealthArticleFinder.pageMatches(MATCHING_ARTICLE, Collections.<String>emptyList()));
    }

    @Test
    public void signsMustBeWholeWordsNotPartsOfOtherWords() {
        String trapping = "<div class=\"Article-content\"><p>Use a carrier with a restraint facility "
                + "during the journey.</p></div>";
        assertFalse(HealthArticleFinder.pageMatches(trapping, Arrays.asList("strain", "urin")));
        assertTrue(HealthArticleFinder.pageMatches(
                "<div class=\"Article-content\"><p>Straining to urinate.</p></div>",
                Arrays.asList("strain", "urin")));
    }

    @Test
    public void signsInTheSiteMenuDoNotCount() {
        String page = "<nav>Urinary problems</nav><div class=\"Article-content\"><p>Straining.</p></div>";
        assertFalse(HealthArticleFinder.pageMatches(page, Arrays.asList("strain", "urin")));
    }

    @Test
    public void pagesNamedAfterTheSignsReplacePassingMentions() {
        List<HealthArticleFinder.Hit> best = HealthArticleFinder.bestCandidates(Arrays.asList(
                new HealthArticleFinder.Hit("Trapping", "/articles/trapping"),
                new HealthArticleFinder.Hit("Feline Lower Urinary Tract Disease", "/articles/flutd")),
                Arrays.asList("strain", "urin"));
        assertEquals(1, best.size());
        assertEquals("/articles/flutd", best.get(0).path);
    }

    @Test
    public void allHitsAreKeptWhenNoTitleNamesTheSigns() {
        List<HealthArticleFinder.Hit> hits = Arrays.asList(
                new HealthArticleFinder.Hit("Trapping", "/articles/trapping"));
        assertEquals(1, HealthArticleFinder.bestCandidates(hits, Arrays.asList("vomit")).size());
    }

    @Test
    public void openingSummaryIsTheIntroAndStaysShort() {
        String summary = HealthArticleFinder.openingSummary(MATCHING_ARTICLE);
        assertTrue(summary.startsWith("Urethral obstruction, otherwise known as a blocked bladder"));
        assertTrue(summary.length() <= 280);
        assertFalse(summary.contains("straining"));
        assertTrue(summary.endsWith("."));
    }
}
