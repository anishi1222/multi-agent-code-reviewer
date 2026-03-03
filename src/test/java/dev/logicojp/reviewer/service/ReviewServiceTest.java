package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.InstructionSource;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        AtomicReference<List<CustomInstruction>> capturedInstructions = new AtomicReference<>();
        AtomicReference<String> capturedOutputConstraints = new AtomicReference<>();

        ReviewService service = new ReviewService(
            null,
            executionConfig,
            templateService,
            (agentConfigs, target, githubToken, overriddenConfig, customInstructions, reasoningEffort, outputConstraints) -> {
                capturedExecution.set(overriddenConfig);
                capturedInstructions.set(customInstructions);
                capturedOutputConstraints.set(outputConstraints);
                return List.of(ReviewResult.builder().success(true).repository(target.displayName()).build());
            }
        );

        List<ReviewResult> results = service.executeReviews(
            Map.of("a", new AgentConfig("a", "A", "m", "s", "i", null, List.of(), List.of())),
            ReviewTarget.local(tempDir),
            null,
            2,
            null,
            "high"
        );

        assertThat(results).hasSize(1);
        assertThat(capturedExecution.get().parallelism()).isEqualTo(2);
        assertThat(capturedInstructions.get()).isEmpty();
        assertThat(capturedOutputConstraints.get()).isEqualTo("constraint-text");
    }

    @Test
    @DisplayName("明示されたcustom instructionsをそのまま渡す")
    void passesExplicitCustomInstructionsAsIs() throws IOException {
        Files.writeString(tempDir.resolve("output-constraints.md"), "constraint-text");
        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            null, null, null, "output-constraints.md", null, null, null));

        ExecutionConfig executionConfig = ExecutionConfig.ofFlat(4, 1, 5, 5, 1, 5, 5, 5, 1, 0, 0, 0);
        AtomicReference<List<CustomInstruction>> capturedInstructions = new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();

        ReviewService service = new ReviewService(
            null,
            executionConfig,
            templateService,
            (agentConfigs, target, githubToken, overriddenConfig, customInstructions, reasoningEffort, outputConstraints) -> {
                invocationCount.incrementAndGet();
                capturedInstructions.set(customInstructions);
                return List.of();
            }
        );

        List<CustomInstruction> instructions = List.of(
            new CustomInstruction("p", "c", InstructionSource.LOCAL_FILE, null, null)
        );

        service.executeReviews(
            Map.of(),
            ReviewTarget.gitHub("o/r"),
            "token",
            4,
            instructions,
            null
        );

        assertThat(invocationCount.get()).isEqualTo(1);
        assertThat(capturedInstructions.get()).isEqualTo(instructions);
    }

    @Test
    @DisplayName("明示されたcustom instructionsは防御的コピーされる")
    void defensivelyCopiesExplicitCustomInstructions() throws IOException {
        Files.writeString(tempDir.resolve("output-constraints.md"), "constraint-text");
        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            null, null, null, "output-constraints.md", null, null, null));

        ExecutionConfig executionConfig = ExecutionConfig.ofFlat(4, 1, 5, 5, 1, 5, 5, 5, 1, 0, 0, 0);
        AtomicReference<List<CustomInstruction>> capturedInstructions = new AtomicReference<>();

        ReviewService service = new ReviewService(
            null,
            executionConfig,
            templateService,
            (agentConfigs, target, githubToken, overriddenConfig, customInstructions, reasoningEffort, outputConstraints) -> {
                capturedInstructions.set(customInstructions);
                return List.of();
            }
        );

        List<CustomInstruction> instructions = new ArrayList<>();
        instructions.add(new CustomInstruction("p", "c", InstructionSource.LOCAL_FILE, null, null));

        service.executeReviews(
            Map.of(),
            ReviewTarget.gitHub("o/r"),
            "token",
            4,
            instructions,
            null
        );

        assertThat(capturedInstructions.get()).isNotSameAs(instructions);
        assertThatThrownBy(() -> capturedInstructions.get().add(
            new CustomInstruction("x", "y", InstructionSource.LOCAL_FILE, null, null)
        )).isInstanceOf(UnsupportedOperationException.class);
    }
}
