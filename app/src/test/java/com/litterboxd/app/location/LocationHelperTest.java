package com.litterboxd.app.location;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocationHelperTest {

    @Test
    public void realPositionsAreAccepted() {
        assertTrue(LocationHelper.isPlausible(31.2745, 120.7385));   // XJTLU
        assertTrue(LocationHelper.isPlausible(-33.8688, 151.2093));  // Sydney
    }

    @Test
    public void emptyOrBrokenPositionsAreRejected() {
        assertFalse(LocationHelper.isPlausible(0, 0));
        assertFalse(LocationHelper.isPlausible(0.0004, -0.0002));
        assertFalse(LocationHelper.isPlausible(Double.NaN, 120.7));
        assertFalse(LocationHelper.isPlausible(95, 120.7));
        assertFalse(LocationHelper.isPlausible(31.2, 190));
    }

    @Test
    public void onlyFixesTakenAfterTheTapCount() {
        long start = 1_000_000_000_000L;   // uptime clock in nanoseconds when the search started
        long wall = 1_800_000_000_000L;    // wall clock in milliseconds at that moment
        assertTrue(LocationHelper.takenAfter(start + 2_000_000_000L, 0, start, wall));   // 2 s later
        assertTrue(LocationHelper.takenAfter(start - 500_000_000L, 0, start, wall));     // within slack
        assertFalse(LocationHelper.takenAfter(start - 300_000_000_000L, 0, start, wall)); // 5 min old
        // No uptime stamp on the fix: fall back to the wall clock.
        assertTrue(LocationHelper.takenAfter(0, wall + 3000, start, wall));
        assertFalse(LocationHelper.takenAfter(0, wall - 600_000, start, wall));
    }
}
