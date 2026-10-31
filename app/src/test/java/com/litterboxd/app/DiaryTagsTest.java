package com.litterboxd.app;

import com.litterboxd.app.util.DiaryTags;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DiaryTagsTest {

    @Test
    public void roundTripKeepsOrder() {
        String stored = DiaryTags.encode(Arrays.asList("play", "first_time"));
        assertEquals("play,first_time", stored);
        assertEquals(Arrays.asList("play", "first_time"), DiaryTags.decode(stored));
    }

    @Test
    public void emptyAndNullAreStoredAsNull() {
        assertNull(DiaryTags.encode(null));
        assertNull(DiaryTags.encode(Collections.emptyList()));
        assertNull(DiaryTags.encode(Arrays.asList(" ", "a,b")));
        assertTrue(DiaryTags.decode(null).isEmpty());
        assertTrue(DiaryTags.decode("").isEmpty());
    }

    @Test
    public void decodeIgnoresBlanksAndDuplicates() {
        assertEquals(Arrays.asList("play", "cuddles"), DiaryTags.decode("play,,cuddles, play"));
    }
}
