package dev.logicojp.reviewer.report.summary;

import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiSummaryClient")
class AiSummaryClientTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("timeout helperはSummaryGenerator互換の値を返す")
    void timeoutHelpersMatchExpectedValues() {
        assertThat(AiSummaryClient.sessionCreateTimeoutMinutes(8)).isEqualTo(2);
        assertThat(AiSummaryClient.sessionCreateTimeoutMinutes(3)).isEqualTo(1);
        assertThat(AiSummaryClient.messageTimeoutMs(5)).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("summary session configにmodel/system prompt/reasoning effortを設定する")
    void createsSummarySessionConfig() throws IOException {
        Files.writeString(tempDir.resolve("summary-system.md"), "SYSTEM SUMMARY");
        AiSummaryClient client = new AiSummaryClient(
            null,
            templateService(),
            "claude-opus-4.8",
            "high",
            5,
            SharedCircuitBreaker.forSummaryDomain()
        );

        var config = client.createSummarySessionConfig();

        assertThat(config.getModel()).isEqualTo("claude-opus-4.8");
        assertThat(config.getSystemMessage().getContent()).isEqualTo("SYSTEM SUMMARY");
        assertThat(config.getReasoningEffort()).isEqualTo("high");
        assertThat(config.getOnPermissionRequest()).isNotNull();
    }

    private TemplateService templateService() {
        return new TemplateService(new TemplateConfig(
            tempDir.toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            new TemplateConfig.SummaryTemplates("summary-system.md", null, null, null, null),
            null
        ));
    }
}
