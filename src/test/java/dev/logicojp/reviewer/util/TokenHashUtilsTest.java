package dev.logicojp.reviewer.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


@DisplayName("TokenHashUtils")
class TokenHashUtilsTest {

    @Nested
    @DisplayName("sha256HexOrEmpty")
    class Sha256HexOrEmpty {

        @Test
        @DisplayName("nullの場合は空文字列を返す")
        void returnsEmptyForNull() {
            Assertions.assertThat(TokenHashUtils.sha256HexOrEmpty(null)).isEmpty();
        }

        @Test
        @DisplayName("空白文字列の場合は空文字列を返す")
        void returnsEmptyForBlank() {
            Assertions.assertThat(TokenHashUtils.sha256HexOrEmpty("   ")).isEmpty();
        }

        @Test
        @DisplayName("有効な値の場合はSHA-256ハッシュを返す")
        void returnsHashForValidValue() {
            String hash = TokenHashUtils.sha256HexOrEmpty("test-token");
            Assertions.assertThat(hash).hasSize(64);
            Assertions.assertThat(hash).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("同じ入力は同じハッシュを返す")
        void sameInputReturnsSameHash() {
            String hash1 = TokenHashUtils.sha256HexOrEmpty("token123");
            String hash2 = TokenHashUtils.sha256HexOrEmpty("token123");
            Assertions.assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("異なる入力は異なるハッシュを返す")
        void differentInputReturnsDifferentHash() {
            String hash1 = TokenHashUtils.sha256HexOrEmpty("token-a");
            String hash2 = TokenHashUtils.sha256HexOrEmpty("token-b");
            Assertions.assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("sha256HexOrNull")
    class Sha256HexOrNull {

        @Test
        @DisplayName("nullの場合はnullを返す")
        void returnsNullForNull() {
            Assertions.assertThat(TokenHashUtils.sha256HexOrNull(null)).isNull();
        }

        @Test
        @DisplayName("空白文字列の場合は空文字列を返す")
        void returnsEmptyForBlank() {
            Assertions.assertThat(TokenHashUtils.sha256HexOrNull("  ")).isEmpty();
        }

        @Test
        @DisplayName("有効な値の場合はSHA-256ハッシュを返す")
        void returnsHashForValidValue() {
            String hash = TokenHashUtils.sha256HexOrNull("test-token");
            Assertions.assertThat(hash).hasSize(64);
            Assertions.assertThat(hash).matches("[0-9a-f]+");
        }
    }
}
