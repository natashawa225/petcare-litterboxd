package com.litterboxd.app;

import com.litterboxd.app.util.AgeUtils;
import org.junit.Test;

import java.util.Calendar;

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

    @Test
    public void testBirthdayPathMatchesApproxPath() {
        Calendar dob = Calendar.getInstance();
        dob.add(Calendar.YEAR, -2);
        dob.add(Calendar.MONTH, -3);
        assertEquals("2y 3m", AgeUtils.getAgeDisplay(dob.getTimeInMillis(), 0, 0));
        assertEquals(AgeUtils.getAgeDisplay(null, 2, 3), AgeUtils.getAgeDisplay(dob.getTimeInMillis(), 0, 0));
        assertEquals("Young Adult", AgeUtils.calculateLifeStage(dob.getTimeInMillis(), 0, 0));
    }

    @Test
    public void testFutureBirthdayClampsToZero() {
        Calendar future = Calendar.getInstance();
        future.add(Calendar.MONTH, 2);
        assertEquals("0 months", AgeUtils.getAgeDisplay(future.getTimeInMillis(), 0, 0));
    }
}
