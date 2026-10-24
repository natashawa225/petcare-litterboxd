package com.litterboxd.app;

import com.litterboxd.app.util.Validators;
import org.junit.Test;
import static org.junit.Assert.*;

public class ValidatorsTest {

    @Test
    public void testCatName() {
        assertTrue(Validators.isValidCatName("Mochi"));
        assertFalse(Validators.isValidCatName("   "));
        assertFalse(Validators.isValidCatName(null));
    }

    @Test
    public void testWeight() {
        assertTrue(Validators.isValidWeight(4.5));
        assertTrue(Validators.isValidWeight(0.5));
        assertTrue(Validators.isValidWeight(15.0));
        assertFalse(Validators.isValidWeight(0.2));
        assertFalse(Validators.isValidWeight(16.0));
    }

    @Test
    public void testPortion() {
        assertTrue(Validators.isValidPortion(50.0));
        assertFalse(Validators.isValidPortion(0.0));
        assertFalse(Validators.isValidPortion(-5.0));
    }

    @Test
    public void testParseDecimal() {
        assertEquals(4.2, Validators.parseDecimal("4.2"), 0.0001);
        assertEquals(4.2, Validators.parseDecimal("4,2"), 0.0001);
        assertEquals(4.2, Validators.parseDecimal(" 4,2 "), 0.0001);
        assertNull(Validators.parseDecimal(""));
        assertNull(Validators.parseDecimal("   "));
        assertNull(Validators.parseDecimal("abc"));
        assertNull(Validators.parseDecimal(null));
    }
}
