package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotService")
class CopilotServiceTest {

    private static CopilotService newService() {
        return new CopilotService(
            new CopilotCliPathResolver(),
            new CopilotCliHealthChecker(new CopilotTimeoutResolver(new CopilotConfig(null, null, null, 60, 10, 15))),
            new CopilotConfig(null, null, null, 60, 10, 15),
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
}
