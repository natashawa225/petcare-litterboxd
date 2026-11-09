package com.litterboxd.app;

import com.litterboxd.app.health.ReadingCard;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Reading Corner's cat-care list and its card rules. */
public class ReadingCardTest {

    @Test
    public void bundledCatCareFileParsesIntoCards() throws IOException, JSONException {
        String json = new String(Files.readAllBytes(
                Paths.get("src/main/res/raw/cat_care_articles.json")), StandardCharsets.UTF_8);
        List<ReadingCard> cards = ReadingCard.parseCatCare(json);
        assertEquals(6, cards.size());
        Set<String> urls = new HashSet<>();
        for (ReadingCard card : cards) {
            assertEquals(ReadingCard.KIND_CAT_CARE, card.getKind());
            assertFalse(card.isForYou());
            assertFalse(card.getTitle().isEmpty());
            assertFalse(card.getSource().isEmpty());
            assertTrue(card.getMinutes() > 0);
            assertTrue(card.getUrl().startsWith("https://"));
            assertTrue("duplicate link " + card.getUrl(), urls.add(card.getUrl()));
        }
    }

    @Test
    public void nonHttpsLinksAreSkipped() throws JSONException {
        List<ReadingCard> cards = ReadingCard.parseCatCare("{\"articles\":["
                + "{\"title\":\"Ok\",\"source\":\"S\",\"url\":\"https://example.org/a\"},"
                + "{\"title\":\"Bad\",\"source\":\"S\",\"url\":\"http://example.org/b\"}]}");
        assertEquals(1, cards.size());
        assertEquals("Ok", cards.get(0).getTitle());
        assertEquals("🐾", cards.get(0).getEmoji()); // default thumbnail
    }

    @Test
    public void logReadingsGetTopicThumbnails() {
        assertEquals("💤", ReadingCard.emojiForReading("source_lethargy"));
        assertEquals("🌧️", ReadingCard.emojiForReading("source_stress"));
        assertEquals("🐾", ReadingCard.emojiForReading("something_else"));
    }
}
