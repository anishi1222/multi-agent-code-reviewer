package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewService")
class ReviewServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("parallelism上書きとoutput constraints読み込みを反映して実行する")
    void appliesParallelismOverrideAndLoadsOutputConstraints() throws IOException {
        Files.writeString(tempDir.resolve("output-constraints.md"), "constraint-text");
        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            null, null, null, "output-constraints.md", null, null, null));

        ExecutionConfig executionConfig = ExecutionConfig.ofFlat(4, 1, 5, 5, 1, 5, 5, 5, 1, 0, 0, 0);
        AtomicReference<ExecutionConfig> capturedExecution = new AtomicReference<>();
        AtomicReference<String> capturedOutputConstraints = new AtomicReference<>();

        ReviewService service = new ReviewService(
            null,
            executionConfig,
            templateService,
            (agentConfigs, target, githubToken, overriddenConfig, reasoningEffort, outputConstraints) -> {
                capturedExecution.set(overriddenConfig);
                capturedOutputConstraints.set(outputConstraints);
                return List.of(ReviewResult.builder().success(true).repository(target.displayName()).build());
            }
        );

        List<ReviewResult> results = service.executeReviews(
            Map.of("a", new AgentConfig("a", "A", "m", "s", "i", null, List.of(), List.of())),
            ReviewTarget.local(tempDir),
            null,
            2,
            "high"
        );

        assertThat(results).hasSize(1);
        assertThat(capturedExecution.get().parallelism()).isEqualTo(2);
        assertThat(capturedOutputConstraints.get()).isEqualTo("constraint-text");
    }
}
