package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotService")
class CopilotServiceTest {

    private static CopilotService newService() {
        return new CopilotService(
            new CopilotCliPathResolver(),
            new CopilotHealthProbe(new CopilotTimeoutResolver(new CopilotConfig(null, null, 60, 10, 15))),
            new CopilotConfig(null, null, 60, 10, 15),
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
    }

    @Test
    @DisplayName("初期状態ではisInitializedはfalse")
    void defaultIsNotInitialized() {
        CopilotService service = newService();

        assertThat(service.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("未初期化状態のshutdownは安全に実行できる")
    void shutdownWithoutInitializeIsSafe() {
        CopilotService service = newService();

        service.shutdown();

        assertThat(service.isInitialized()).isFalse();
    }

    @Nested
    @DisplayName("SDKログレベル正規化")
    class NormalizeSdkLogLevel {

        @Test
        @DisplayName("warnはwarningへ正規化される")
        void warnIsNormalizedToWarning() {
            assertThat(CopilotService.normalizeSdkLogLevel("warn")).contains("warning");
        }

        @Test
        @DisplayName("offはnoneへ正規化される")
        void offIsNormalizedToNone() {
            assertThat(CopilotService.normalizeSdkLogLevel("off")).contains("none");
        }

        @Test
        @DisplayName("traceはdebugへ正規化される")
        void traceIsNormalizedToDebug() {
            assertThat(CopilotService.normalizeSdkLogLevel("trace")).contains("debug");
        }

        @Test
        @DisplayName("有効なCLIログレベルはそのまま保持される")
        void validCliLogLevelIsPreserved() {
            assertThat(CopilotService.normalizeSdkLogLevel("warning")).contains("warning");
            assertThat(CopilotService.normalizeSdkLogLevel("all")).contains("all");
        }

        @Test
        @DisplayName("無効なログレベルはemptyを返す")
        void invalidLogLevelReturnsEmpty() {
            assertThat(CopilotService.normalizeSdkLogLevel("verbose")).isEmpty();
        }
    }
}
