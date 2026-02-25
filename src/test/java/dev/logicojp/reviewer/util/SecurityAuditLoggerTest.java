package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;


@DisplayName("SecurityAuditLogger")
class SecurityAuditLoggerTest {

    @Nested
    @DisplayName("log - 基本動作")
    class BasicLogging {

        @Test
        @DisplayName("3引数でログ出力できる")
        void logsWithThreeArgs() {
            // SecurityAuditLogger is static; verify no exceptions thrown
            SecurityAuditLogger.log("authentication", "login", "User logged in");
        }

        @Test
        @DisplayName("4引数でログ出力できる")
        void logsWithFourArgs() {
            SecurityAuditLogger.log("authentication", "login", "User logged in",
                Map.of("userId", "user123"));
        }

        @Test
        @DisplayName("null属性で例外を投げない")
        void handlesNullAttributes() {
            SecurityAuditLogger.log("test", "action", "message", null);
        }

        @Test
        @DisplayName("空のキーはスキップされる")
        void handlesBlankKeys() {
            SecurityAuditLogger.log("test", "action", "message",
                Map.of("", "value"));
        }
    }
}
