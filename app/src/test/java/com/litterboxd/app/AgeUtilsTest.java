package com.litterboxd.app;

import com.litterboxd.app.util.AgeUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgeUtilsTest {

    @Test
    public void testLifeStageKitten() {
        assertEquals("Kitten", AgeUtils.calculateLifeStage(null, 0, 6));
    }

    @Test
    public void testLifeStageYoungAdult() {
        assertEquals("Young Adult", AgeUtils.calculateLifeStage(null, 3, 0));
    }

    @Test
    public void testLifeStageMatureAdult() {
        assertEquals("Mature Adult", AgeUtils.calculateLifeStage(null, 8, 0));
    }

    @Test
    public void testLifeStageSenior() {
        assertEquals("Senior", AgeUtils.calculateLifeStage(null, 12, 0));
    }

    @Test
    public void testLifeStageGeriatric() {
        assertEquals("Geriatric", AgeUtils.calculateLifeStage(null, 16, 0));
    }
}
