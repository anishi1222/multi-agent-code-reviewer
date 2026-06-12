package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenReadUtils")
class TokenReadUtilsTest {

    @Test
    @DisplayName("パスワード入力経路ではtrimしてchar配列をクリアする")
    void trimsAndClearsPasswordBuffer() throws Exception {
        char[] password = "  secret-token  ".toCharArray();

        char[] token = TokenReadUtils.readTrimmedTokenChars(
            () -> password,
            _ -> new byte[0],
            256
        );

        try {
            assertThat(new String(token)).isEqualTo("secret-token");
            assertThat(password).containsOnly('\0');
        } finally {
            Arrays.fill(token, '\0');
        }
    }

    @Test
    @DisplayName("標準入力経路ではtrimしてbyte配列をクリアする")
    void trimsAndClearsStdinBuffer() throws Exception {
        byte[] input = "  stdin-token\n".getBytes(StandardCharsets.UTF_8);

        char[] token = TokenReadUtils.readTrimmedTokenChars(
            () -> null,
            _ -> input,
            256
        );

        try {
            assertThat(new String(token)).isEqualTo("stdin-token");
            for (byte b : input) {
                assertThat(b).isZero();
            }
        } finally {
            Arrays.fill(token, '\0');
        }
    }
}
