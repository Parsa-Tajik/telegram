package com.telegram.telegrampromium.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2-SHA256 password hasher that outputs a PHC-style string:
 *   $pbkdf2-sha256$100000$<saltB64>$<hashB64>
 *
 * Salt is derived deterministically from username to keep a single-field payload (pwd) client-side,
 * while still avoiding plain-text. This is NOT a replacement for server-side hashing.
 */
public final class PasswordHasher {

    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";
    private static final int    ITERATIONS  = 100_000;
    private static final int    KEY_LEN     = 256; // bits
    private static final int    SALT_LEN    = 16;  // bytes

    private PasswordHasher() {}

    /**
     * Builds PHC string from username + password chars.
     *
     * @param username used to derive a deterministic salt
     * @param password clear-text password (not stored)
     * @return PHC string
     */
    public static String phc(String username, char[] password) {
        byte[] salt = deriveSaltFromUsername(username);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LEN);

        String sB64 = Base64.getEncoder().encodeToString(salt);
        String hB64 = Base64.getEncoder().encodeToString(hash);
        return "$pbkdf2-sha256$" + ITERATIONS + "$" + sB64 + "$" + hB64;
    }

    /**
     * Derives a stable 16-byte salt from a normalized username.
     * This helps keep the client payload to a single "pwd" field.
     */
    private static byte[] deriveSaltFromUsername(String username) {
        String u = username == null ? "" : username.trim().toLowerCase();
        byte[] seed = ("tpfx:v1:" + u).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(seed);
            byte[] salt = new byte[SALT_LEN];
            System.arraycopy(digest, 0, salt, 0, SALT_LEN);
            return salt;
        } catch (Exception e) {
            // Fallback: random salt if algorithm is unavailable (unlikely on a standard JRE)
            byte[] salt = new byte[SALT_LEN];
            new SecureRandom().nextBytes(salt);
            return salt;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLenBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGO);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 failure", e);
        }
    }
}
