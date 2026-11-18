package dev.logicojp.reviewer.infrastructure.copilot;

import com.microsoft.copilot.rpc.CopilotClientMode;
import com.microsoft.copilot.rpc.SpawnedStdioRuntimeConnection;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CopilotService")
class CopilotServiceTest {

    private static CopilotService newService() {
        var config = new CopilotConfig(null, null, 60, 10, 15);
        return new CopilotService(
            new CopilotCliPathResolver(config),
            new CopilotHealthProbe(config),
            config,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
    }

    @Test
    @DisplayName("JVMとNative Imageで検証済みCLIをstdioで起動し、既存のログインを利用する")
    void usesResolvedCliInsteadOfDefaultInProcessRuntime() {
        var options = CopilotService.buildOptions("/opt/bin/copilot", "warning");

        assertThat(options.getRuntime()).isInstanceOfSatisfying(SpawnedStdioRuntimeConnection.class,
            runtime -> assertThat(runtime.getPath()).isEqualTo("/opt/bin/copilot"));
        assertThat(options.getMode()).isEqualTo(CopilotClientMode.CODING_AGENT);
        assertThat(options.getGitHub().getUseLoggedInUser()).contains(true);
        assertThat(options.getObservability().getLogLevel()).isEqualTo("warning");
    }

    @Test
    @DisplayName("初期状態ではhealthyはfalse")
    void defaultIsNotHealthy() {
        CopilotService service = newService();

        assertThat(service.isHealthy()).isFalse();
        assertThatThrownBy(service::getClient)
            .hasMessageContaining("Copilot client");
    }

    @Test
    @DisplayName("未初期化状態のstopは安全に実行できる")
    void stopWithoutInitializeIsSafe() {
        CopilotService service = newService();

        service.stop();

        assertThat(service.isHealthy()).isFalse();
    }

    // removed: normalizeSdkLogLevel alias mapping was deleted; CopilotService now only accepts exact SDK log levels from COPILOT_SDK_LOG_LEVEL internally.
}
