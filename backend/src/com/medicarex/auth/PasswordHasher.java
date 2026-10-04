package com.medicarex.auth;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {
    private static final int BCRYPT_LOG_ROUNDS = 12;

    /**
     * Hashes a password using BCrypt with log rounds 12.
     */
    public static String hashPassword(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        return BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * Backward-compatible hash method matching (password, salt).
     */
    public static String hashPassword(String plaintextPassword, String salt) {
        return hashPassword(plaintextPassword);
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     * Also supports fallback verification for migration.
     */
    public static boolean verifyPassword(String plaintextPassword, String salt, String storedHash) {
        if (plaintextPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        // 1. Verify standard BCrypt hash ($2a$, $2b$, $2y$)
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(plaintextPassword, storedHash);
            } catch (Exception e) {
                return false;
            }
        }

        // 2. Legacy / plaintext fallback check (for initial DB seed migration)
        if (plaintextPassword.equals(storedHash)) {
            return true;
        }

        return false;
    }

    public static String generateSalt() {
        return BCrypt.gensalt(BCRYPT_LOG_ROUNDS);
    }

    public static String getDefaultSalt() {
        return "";
    }
}
