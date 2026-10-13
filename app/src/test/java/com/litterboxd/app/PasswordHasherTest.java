package com.litterboxd.app;

import com.litterboxd.app.auth.PasswordHasher;

import org.junit.Test;

import static org.junit.Assert.*;

public class PasswordHasherTest {

    @Test
    public void testPasswordHashingAndVerification() {
        String password = "secureCatPassword123";
        PasswordHasher.HashResult result = PasswordHasher.hashPassword(password);

        assertNotNull(result.hash);
        assertNotNull(result.salt);

        // Verify with correct password and salt
        assertTrue(PasswordHasher.verify(password, result.hash, result.salt));

        // Verify with wrong password
        assertFalse(PasswordHasher.verify("wrongPassword", result.hash, result.salt));
    }

    @Test
    public void testDifferentSaltsProduceDifferentHashes() {
        String password = "samePassword";
        PasswordHasher.HashResult result1 = PasswordHasher.hashPassword(password);
        PasswordHasher.HashResult result2 = PasswordHasher.hashPassword(password);

        assertNotEquals(result1.salt, result2.salt);
        assertNotEquals(result1.hash, result2.hash);

        // Both verify correctly with their respective salts
        assertTrue(PasswordHasher.verify(password, result1.hash, result1.salt));
        assertTrue(PasswordHasher.verify(password, result2.hash, result2.salt));
    }
}
