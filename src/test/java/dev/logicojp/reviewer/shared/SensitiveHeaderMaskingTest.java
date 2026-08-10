package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveHeaderMasking")
class SensitiveHeaderMaskingTest {

    // ADR-0007 D5 deleted `wrapHeaders`/`MaskedHeadersMap`. The two tests that used to live here
    // asserted that a wrapped map masked in `toString()` while `get()` returned the raw value --
    // which is exactly the half-measure the ADR removed: everything except `toString()` leaked, and
    // the guard vanished on the first copy. Masking is now the log sink's job; the behavioural
    // control for it is `SensitiveHeaderMaskingSinkCanaryTest`, and `LayerDependencyRulesTest`
    // Rule 4b stops a port from depending on this class again.
    //
    // What is left here is the pure API the ADR kept: judgement (`isSensitiveHeaderName`) and
    // formatting (`maskHeaderValue`, `maskSensitiveValue`, `buildMaskedMapString`).

    @Test
    @DisplayName("buildMaskedMapStringは機密ヘッダーのみをマスクする")
    void buildsMaskedMapStringMaskingOnlySensitiveHeaders() {
        Map<String, String> headers = Map.of(
            "Authorization", "Bearer secret-token",
            "X-Request-Id", "abc"
        );

        String masked = SensitiveHeaderMasking.buildMaskedMapString(headers);

        assertThat(masked).doesNotContain("secret-token");
        assertThat(masked).contains("X-Request-Id=abc");
    }

    @Test
    @DisplayName("maskHeaderValueはヘッダー名で判定し非機密値はそのまま返す")
    void masksByHeaderNameAndPassesThroughBenignValues() {
        assertThat(SensitiveHeaderMasking.maskHeaderValue("Authorization", "Bearer secret-token"))
            .doesNotContain("secret-token");
        assertThat(SensitiveHeaderMasking.maskHeaderValue("X-Request-Id", "abc"))
            .isEqualTo("abc");
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
