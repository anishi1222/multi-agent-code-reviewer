package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewRunRequestFactory")
class ReviewRunRequestFactoryTest {

    @Test
    @DisplayName("実行リクエストへ必要項目を正しく転送する")
    void createsRunRequestWithExpectedFields() {
        var factory = new ReviewRunRequestFactory();
        var options = new ReviewCommand.ParsedOptions(
            new ReviewCommand.TargetSelection.Repository("owner/repo"),
            new ReviewCommand.AgentSelection.All(),
            Path.of("./reports"),
            List.of(),
            "ghp_token",
            3,
            true,
            "review-model",
            "report-model",
            "summary-model",
            "default-model",
            false
        );
        var target = ReviewTarget.gitHub("owner/repo");
        var modelConfig = new ModelConfig("review-model", "report-model", "summary-model", "high", "default-model");
        var agentConfigs = Map.of("code-quality", new AgentConfig("code-quality", "Code Quality", "review-model", "prompt", "instruction", "", List.of(), List.of()));
        var outputDirectory = Path.of("./reports/owner/repo");

        ReviewRunExecutor.ReviewRunRequest request = factory.create(
            options,
            target,
            modelConfig,
            agentConfigs,
            outputDirectory
        );

        assertThat(request.target()).isEqualTo(target);
        assertThat(request.summaryModel()).isEqualTo("summary-model");
        assertThat(request.reasoningEffort()).isEqualTo("high");
        assertThat(request.agentConfigs()).isEqualTo(agentConfigs);
        assertThat(request.parallelism()).isEqualTo(3);
        assertThat(request.noSummary()).isTrue();
        assertThat(request.outputDirectory()).isEqualTo(outputDirectory);
    }
}
