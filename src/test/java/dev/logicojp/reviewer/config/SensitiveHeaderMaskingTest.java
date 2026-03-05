package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveHeaderMasking")
class SensitiveHeaderMaskingTest {

    @Test
    @DisplayName("AuthorizationヘッダーはtoStringでマスクされる")
    void masksSensitiveHeadersInToString() {
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer secret-token",
            "X-Request-Id", "abc"
        );

        Map<String, String> wrapped = SensitiveHeaderMasking.wrapHeaders(headers);

        assertThat(wrapped.get("Authorization")).isEqualTo("Bearer secret-token");
        assertThat(wrapped.toString()).contains("Authorization=Bearer ***");
        assertThat(wrapped.toString()).contains("X-Request-Id=abc");
    }

    @Test
    @DisplayName("values()のiterator経由でも機密ヘッダー値をマスクする")
    void masksSensitiveHeadersInValuesIterator() {
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer secret-token",
            "X-Request-Id", "abc"
        );

        Map<String, String> wrapped = SensitiveHeaderMasking.wrapHeaders(headers);
        Iterator<String> values = wrapped.values().iterator();
        List<String> collected = List.of(values.next(), values.next());

        assertThat(collected).containsExactlyInAnyOrder("Bearer ***", "abc");
    }

    @Test
    @DisplayName("token文字列を含むヘッダー名を大文字小文字無視で判定する")
    void detectsSensitiveHeaderNameCaseInsensitively() {
        assertThat(SensitiveHeaderMasking.isSensitiveHeaderName("X-Access-Token")).isTrue();
        assertThat(SensitiveHeaderMasking.isSensitiveHeaderName("authorization")).isTrue();
        assertThat(SensitiveHeaderMasking.isSensitiveHeaderName("X-Api-Key")).isTrue();
        assertThat(SensitiveHeaderMasking.isSensitiveHeaderName("Set-Cookie")).isTrue();
        assertThat(SensitiveHeaderMasking.isSensitiveHeaderName("Db-Password")).isTrue();
        assertThat(SensitiveHeaderMasking.isSensitiveHeaderName("Content-Type")).isFalse();
    }

    @Test
    @DisplayName("機密値のマスクはプレフィックスを保持する")
    void masksSensitiveValueWithPrefix() {
        assertThat(SensitiveHeaderMasking.maskSensitiveValue("Bearer abc.def.ghi"))
            .isEqualTo("Bearer ***");
        assertThat(SensitiveHeaderMasking.maskSensitiveValue("   ")).isEqualTo("***");
    }
}
