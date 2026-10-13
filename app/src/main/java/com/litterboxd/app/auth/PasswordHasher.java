package com.litterboxd.app.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Handles secure password hashing and verification using PBKDF2WithHmacSHA256,
 * random 16-byte salt, 120,000 iterations, and constant-time comparison.
 * Uses standard Java java.util.Base64 for compatibility with both JVM unit tests and Android runtime.
 */
public class PasswordHasher {
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    public static class HashResult {
        public final String hash;
        public final String salt;

        public HashResult(String hash, String salt) {
            this.hash = hash;
            this.salt = salt;
        }
    }

    /**
     * Generates a secure random 16-byte salt and hashes the password with PBKDF2WithHmacSHA256.
     */
    public static HashResult hashPassword(String password) {
        try {
            byte[] saltBytes = new byte[SALT_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(saltBytes);

            byte[] hashBytes = pbkdf2(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);

            String saltStr = Base64.getEncoder().encodeToString(saltBytes);
            String hashStr = Base64.getEncoder().encodeToString(hashBytes);
            return new HashResult(hashStr, saltStr);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Hashes a password with an existing salt and verifies if it matches the stored hash
     * using constant-time comparison (MessageDigest.isEqual) to prevent timing attacks.
     */
    public static boolean verify(String password, String storedHash, String storedSalt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(storedSalt);
            byte[] expectedHashBytes = Base64.getDecoder().decode(storedHash);

            byte[] actualHashBytes = pbkdf2(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);

            return MessageDigest.isEqual(expectedHashBytes, actualHashBytes);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }
}
