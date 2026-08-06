package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenHashUtils")
class TokenHashUtilsTest {

    @Nested
    @DisplayName("sha256HexOrEmpty")
    class Sha256HexOrEmpty {

        @Test
        @DisplayName("nullの場合は空文字列を返す")
        void returnsEmptyForNull() {
            assertThat(TokenHashUtils.sha256HexOrEmpty(null)).isEmpty();
        }

        @Test
        @DisplayName("空文字列の場合は空文字列を返す")
        void returnsEmptyForEmpty() {
            assertThat(TokenHashUtils.sha256HexOrEmpty("")).isEmpty();
        }

        @Test
        @DisplayName("空白のみの場合は空文字列を返す")
        void returnsEmptyForBlank() {
            assertThat(TokenHashUtils.sha256HexOrEmpty("   ")).isEmpty();
        }

        @Test
        @DisplayName("同一入力に対して同一ハッシュを返す（冪等性）")
        void idempotent() {
            String hash1 = TokenHashUtils.sha256HexOrEmpty("test-token");
            String hash2 = TokenHashUtils.sha256HexOrEmpty("test-token");
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("ハッシュ値は64文字の16進数文字列")
        void hashIs64HexChars() {
            String hash = TokenHashUtils.sha256HexOrEmpty("test-token");
            assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("異なる入力に対して異なるハッシュを返す")
        void differentInputsProduceDifferentHashes() {
            String hash1 = TokenHashUtils.sha256HexOrEmpty("token-a");
            String hash2 = TokenHashUtils.sha256HexOrEmpty("token-b");
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("sha256HexOrNull")
    class Sha256HexOrNull {

        @Test
        @DisplayName("nullの場合はnullを返す")
        void returnsNullForNull() {
            assertThat(TokenHashUtils.sha256HexOrNull(null)).isNull();
        }

        @Test
        @DisplayName("空文字列の場合は空文字列を返す")
        void returnsEmptyForEmpty() {
            assertThat(TokenHashUtils.sha256HexOrNull("")).isEmpty();
        }

        @Test
        @DisplayName("空白のみの場合は空文字列を返す")
        void returnsEmptyForBlank() {
            assertThat(TokenHashUtils.sha256HexOrNull("   ")).isEmpty();
        }

        @Test
        @DisplayName("通常のトークンに対してハッシュ値を返す")
        void returnsHashForToken() {
            String hash = TokenHashUtils.sha256HexOrNull("my-secret-token");
            assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("sha256HexOrEmptyと同一のハッシュ値を返す")
        void consistentWithSha256HexOrEmpty() {
            String token = "consistent-test";
            assertThat(TokenHashUtils.sha256HexOrNull(token))
                .isEqualTo(TokenHashUtils.sha256HexOrEmpty(token));
        }
    }
}
