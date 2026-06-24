package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenInputReader")
class TokenInputReaderTest {

    private final TokenInputReader reader = new TokenInputReader(() -> null, _ -> new byte[0]);

    @Test
    @DisplayName("null/blank tokenはnullに正規化し、通常tokenはtrimする")
    void normalizesProvidedToken() {
        assertThat(reader.normalize(null)).isNull();
        assertThat(reader.normalize("   ")).isNull();
        assertThat(reader.normalize("  ghp_token  ")).isEqualTo("ghp_token");
    }

    @Test
    @DisplayName("'-' 指定時はstdinからtrim済みtokenを読む")
    void readsTokenFromStdinSentinel() {
        var stdinReader = new TokenInputReader(
            () -> null,
            _ -> "  ghp_stdin\n".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(stdinReader.normalize("-")).isEqualTo("ghp_stdin");
    }

    @Test
    @DisplayName("console password readerが値を返した場合はstdinを読まない")
    void prefersPasswordReaderOverStdin() {
        var consoleReader = new TokenInputReader(
            () -> "  ghp_console  ".toCharArray(),
            _ -> {
                throw new AssertionError("stdin should not be read when console password is available");
            }
        );

        assertThat(consoleReader.normalize("-")).isEqualTo("ghp_console");
    }
}
