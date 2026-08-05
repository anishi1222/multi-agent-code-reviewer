package dev.logicojp.reviewer.infrastructure.auth;

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
    }

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

    static char[] trimToNewArray(char[] chars) {
        int start = 0;
        int end = chars.length;
        while (start < end && (chars[start] == '\n' || chars[start] == '\r' || chars[start] == ' ')) start++;
        while (end > start && (chars[end - 1] == '\n' || chars[end - 1] == '\r' || chars[end - 1] == ' ')) end--;
        return Arrays.copyOfRange(chars, start, end);
    }

    static char[] decodeUtf8(byte[] raw) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(raw);
        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
        try {
            return Arrays.copyOf(charBuffer.array(), charBuffer.limit());
        } finally {
            Arrays.fill(charBuffer.array(), '\0');
        }
    }
}
