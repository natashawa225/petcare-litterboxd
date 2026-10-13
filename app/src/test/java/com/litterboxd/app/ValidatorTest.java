package com.litterboxd.app;

import com.litterboxd.app.auth.Validator;

import org.junit.Test;

import static org.junit.Assert.*;

public class ValidatorTest {

    @Test
    public void testEmailValidation() {
        assertTrue(Validator.isValidEmail("test@example.com"));
        assertTrue(Validator.isValidEmail("  user.name@domain.co.uk  "));
        assertFalse(Validator.isValidEmail("invalid-email"));
        assertFalse(Validator.isValidEmail("@domain.com"));
        assertFalse(Validator.isValidEmail("user@"));
        assertFalse(Validator.isValidEmail(""));
        assertFalse(Validator.isValidEmail(null));
    }

    @Test
    public void testPasswordValidation() {
        assertTrue(Validator.isValidPassword("12345678"));
        assertTrue(Validator.isValidPassword("verySecurePassword!"));
        assertFalse(Validator.isValidPassword("short"));
        assertFalse(Validator.isValidPassword(""));
        assertFalse(Validator.isValidPassword(null));
    }

    @Test
    public void testPasswordsMatch() {
        assertTrue(Validator.doPasswordsMatch("password123", "password123"));
        assertFalse(Validator.doPasswordsMatch("password123", "different123"));
        assertFalse(Validator.doPasswordsMatch("password123", null));
        assertFalse(Validator.doPasswordsMatch(null, null));
    }
}
