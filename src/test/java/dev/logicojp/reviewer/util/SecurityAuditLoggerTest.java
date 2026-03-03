package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityAuditLogger")
class SecurityAuditLoggerTest {

    @Test
    @DisplayName("ログ値サニタイズでCRLFを空白に置換する")
    void sanitizesCrLfInLogValues() {
        String sanitized = SecurityAuditLogger.sanitizeForLogValue("line1\nline2\rline3");

        assertThat(sanitized).isEqualTo("line1 line2 line3");
    }

    @Test
    @DisplayName("nullのログ値は空文字に変換する")
    void sanitizesNullLogValue() {
        assertThat(SecurityAuditLogger.sanitizeForLogValue(null)).isEmpty();
    }

    @Test
    @DisplayName("監査ログ出力後にMDCをクリーンアップする")
    void clearsAuditMdcEntriesAfterLogging() {
        MDC.put("keep.me", "value");

        SecurityAuditLogger.log(
            "access",
            "review.start",
            "review started",
            Map.of("requestId", "abc-123")
        );

        assertThat(MDC.get("event.category")).isNull();
        assertThat(MDC.get("event.action")).isNull();
        assertThat(MDC.get("audit.requestId")).isNull();
        assertThat(MDC.get("keep.me")).isEqualTo("value");

        MDC.remove("keep.me");
    }

    @Test
    @DisplayName("null属性でも例外を投げず出力できる")
    void acceptsNullAttributes() {
        SecurityAuditLogger.log("access", "review.start", "review started", null);

        assertThat(MDC.get("event.category")).isNull();
        assertThat(MDC.get("event.action")).isNull();
    }
}
