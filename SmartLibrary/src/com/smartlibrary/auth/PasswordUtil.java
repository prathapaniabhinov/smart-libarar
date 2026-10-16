package com.smartlibrary.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (standard Java library only).
 * <p>
 * Stored format: {@code pbkdf2$<iterations>$<base64 salt>$<base64 hash>}.
 * Anything not in this format (for example {@code HASH_PLACEHOLDER}) never verifies.
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** Hashes a password with a fresh random salt. */
    public static String hash(char[] password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is null");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_BITS);
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(hash);
    }

    /** Verifies a password against a stored hash. Returns false for malformed or placeholder hashes. */
    public static boolean verify(char[] password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1 || iterations > 10_000_000) {
                return false;
            }
            Base64.Decoder dec = Base64.getDecoder();
            byte[] salt = dec.decode(parts[2]);
            byte[] expected = dec.decode(parts[3]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Burns roughly the same CPU time as a real verification. Used when a username does not exist,
     * so response time does not reveal which usernames are registered.
     */
    public static void dummyVerify(char[] password) {
        pbkdf2(password == null ? new char[0] : password, new byte[SALT_BYTES], ITERATIONS, KEY_BITS);
    }

    /**
     * Checks password strength.
     *
     * @return an error message, or {@code null} if the password is acceptable
     */
    public static String validateStrength(char[] password) {
        if (password == null || password.length < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters long.";
        }
        if (password.length > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters long.";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (Character.isWhitespace(c)) {
                return "Password must not contain spaces.";
            }
        }
        if (!hasLetter || !hasDigit) {
            return "Password must contain at least one letter and one digit.";
        }
        return null;
    }

    /** Overwrites a password array in memory. */
    public static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 is not available in this Java runtime", e);
        } finally {
            spec.clearPassword();
        }
    }
}
