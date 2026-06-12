package dev.logicojp.reviewer.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/// Shared utility for securely reading tokens from console/password input or stdin.
public final class TokenReadUtils {

    @FunctionalInterface
    public interface PasswordReader {
        char[] readPassword();
    }

    @FunctionalInterface
    public interface StdinReader {
        byte[] readStdin(int maxBytes) throws IOException;
    }

    private TokenReadUtils() {
        // Utility class
    }

    /// Reads a token into a trimmed char[] and clears temporary char[]/byte[] buffers.
    ///
    /// The returned array must be cleared by the caller after use.
    public static char[] readTrimmedTokenChars(PasswordReader passwordReader,
                                               StdinReader stdinReader,
                                               int maxBytes) throws IOException {
        char[] passwordChars = passwordReader.readPassword();
        if (passwordChars != null) {
            try {
                return trimToNewArray(passwordChars);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        }

        byte[] raw = stdinReader.readStdin(maxBytes);
        try {
            char[] decoded = decodeUtf8(raw);
            try {
                return trimToNewArray(decoded);
            } finally {
                Arrays.fill(decoded, '\0');
            }
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    /// Compatibility helper for call sites that still require a String.
    /// Prefer {@link #readTrimmedTokenChars(PasswordReader, StdinReader, int)}.
    public static String readTrimmedToken(PasswordReader passwordReader,
                                          StdinReader stdinReader,
                                          int maxBytes) throws IOException {
        char[] tokenChars = readTrimmedTokenChars(passwordReader, stdinReader, maxBytes);
        try {
            return new String(tokenChars);
        } finally {
            Arrays.fill(tokenChars, '\0');
        }
    }

    private static char[] decodeUtf8(byte[] raw) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(raw));
        char[] chars = new char[decoded.remaining()];
        decoded.get(chars);
        return chars;
    }

    private static char[] trimToNewArray(char[] input) {
        int start = 0;
        int end = input.length;

        while (start < end && Character.isWhitespace(input[start])) {
            start++;
        }
        while (end > start && Character.isWhitespace(input[end - 1])) {
            end--;
        }

        int length = end - start;
        if (length == 0) {
            return new char[0];
        }
        char[] trimmed = new char[length];
        System.arraycopy(input, start, trimmed, 0, length);
        return trimmed;
    }
}
