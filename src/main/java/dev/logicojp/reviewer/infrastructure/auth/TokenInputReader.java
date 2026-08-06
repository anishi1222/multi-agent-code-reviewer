package dev.logicojp.reviewer.infrastructure.auth;

import dev.logicojp.reviewer.shared.TokenReadUtils;

import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

final class TokenInputReader {

    private static final Logger logger = LoggerFactory.getLogger(TokenInputReader.class);
    private static final String STDIN_TOKEN_SENTINEL = "-";
    private static final int MAX_STDIN_TOKEN_BYTES = 256;
    private final TokenReadUtils.PasswordReader passwordReader;
    private final TokenReadUtils.StdinReader stdinReader;

    TokenInputReader() {
        this(
            () -> System.console() == null ? null : System.console().readPassword("GitHub Token: "),
            System.in::readNBytes
        );
    }

    TokenInputReader(TokenReadUtils.PasswordReader passwordReader, TokenReadUtils.StdinReader stdinReader) {
        this.passwordReader = passwordReader;
        this.stdinReader = stdinReader;
    }

    @Nullable String normalize(@Nullable String token) {
        if (token == null) return null;
        String trimmed = token.trim();
        if (STDIN_TOKEN_SENTINEL.equals(trimmed)) return readTokenFromStdin();
        if (trimmed.isEmpty()) return null;
        return trimmed;
    }

    private @Nullable String readTokenFromStdin() {
        try {
            char[] chars = TokenReadUtils.readTrimmedTokenChars(passwordReader, stdinReader, MAX_STDIN_TOKEN_BYTES);
            try {
                if (chars.length == 0) return null;
                return new String(chars);
            } finally {
                Arrays.fill(chars, '\0');
            }
        } catch (IOException e) {
            logger.warn("Failed to read token from stdin: {}", e.getMessage());
            return null;
        }
    }
}
