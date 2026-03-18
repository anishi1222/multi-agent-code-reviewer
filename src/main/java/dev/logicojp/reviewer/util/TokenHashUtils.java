package dev.logicojp.reviewer.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// Utility methods for hashing tokens before cache keys or audit-safe logging.
public final class TokenHashUtils {

    private TokenHashUtils() {
    }

    public static String sha256HexOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return sha256Hex(value);
    }

    public static String sha256HexOrNull(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return "";
        }
        return sha256Hex(value);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}