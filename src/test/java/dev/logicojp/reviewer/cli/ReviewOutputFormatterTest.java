package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewOutputFormatter")
class ReviewOutputFormatterTest {

    @Test
    @DisplayName("バナー出力に主要情報が含まれる")
    void printBannerIncludesTargetAgentsAndModels() {
        var outBuffer = new ByteArrayOutputStream();
        var errBuffer = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(outBuffer), new PrintStream(errBuffer));
        var formatter = new ReviewOutputFormatter(output, ExecutionConfig.ofFlat(2, 1, 10, 5, 5, 5, 5, 10, 1, 0, 0, 0));

        AgentConfig config = new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of());
        formatter.printBanner(
            Map.of("security", config),
            List.of(),
            new ModelConfig("review", "report", "summary", "high", "default"),
            ReviewTarget.gitHub("owner/repo"),
            java.nio.file.Path.of("reports/owner/repo"),
            "review-model"
        );

        String outputText = outBuffer.toString();
        assertThat(outputText).contains("Multi-Agent Code Reviewer");
        assertThat(outputText).contains("owner/repo");
        assertThat(outputText).contains("review-model");
    }

    @Test
    @DisplayName("完了サマリに成功件数と失敗件数を表示する")
    void printCompletionSummaryShowsSuccessAndFailureCounts() {
        var outBuffer = new ByteArrayOutputStream();
        var errBuffer = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(outBuffer), new PrintStream(errBuffer));
        var formatter = new ReviewOutputFormatter(output, ExecutionConfig.ofFlat(2, 1, 10, 5, 5, 5, 5, 10, 1, 0, 0, 0));

        AgentConfig config = new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of());
        List<ReviewResult> results = List.of(
            ReviewResult.builder().agentConfig(config).repository("owner/repo").content("ok").success(true).timestamp(Instant.now()).build(),
            ReviewResult.builder().agentConfig(config).repository("owner/repo").success(false).errorMessage("failed").timestamp(Instant.now()).build()
        );

        formatter.printCompletionSummary(results, java.nio.file.Path.of("reports/owner/repo"));

        String outputText = outBuffer.toString();
        assertThat(outputText).contains("Successful: 1");
        assertThat(outputText).contains("Failed: 1");
    }
}
