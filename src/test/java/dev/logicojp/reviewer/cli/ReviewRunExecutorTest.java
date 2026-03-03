package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewRunExecutor")
class ReviewRunExecutorTest {

    @Test
    @DisplayName("noSummary=true の場合はサマリー生成を実行しない")
    void doesNotGenerateSummaryWhenNoSummaryIsTrue() {
        CliOutput cliOutput = new CliOutput(
            new PrintStream(OutputStream.nullOutputStream()),
            new PrintStream(OutputStream.nullOutputStream())
        );
        ReviewOutputFormatter formatter = new ReviewOutputFormatter(
            cliOutput,
            ExecutionConfig.ofFlat(1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0)
        );

        AtomicBoolean summaryCalled = new AtomicBoolean(false);

        ReviewRunExecutor executor = new ReviewRunExecutor(
            null,
            null,
            formatter,
            cliOutput,
            (resolvedToken, context) -> List.of(successResult("agent-a", context.target().displayName())),
            (results, outputDirectory) -> List.of(outputDirectory.resolve("agent-a-report.md")),
            (results, context) -> {
                summaryCalled.set(true);
                return context.outputDirectory().resolve("executive-summary.md");
            }
        );

        ReviewRunExecutor.ReviewRunRequest request = new ReviewRunExecutor.ReviewRunRequest(
            ReviewTarget.gitHub("owner/repo"),
            "model",
            "high",
            Map.of("agent-a", new AgentConfig("agent-a", "Agent A", "model", "system", "instruction", null, List.of(), List.of())),
            1,
            true,
            List.of(),
            Path.of("reports")
        );

        int exitCode = executor.execute("token", request);

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
        assertThat(summaryCalled).isFalse();
    }

    private static ReviewResult successResult(String agentName, String repository) {
        AgentConfig config = new AgentConfig(agentName, agentName, "model", "system", "instruction", null, List.of(), List.of());
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository)
            .content("ok")
            .success(true)
            .build();
    }
}
