package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
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
        var options = ReviewOptions.builder()
            .target(new ReviewTargetSelection.Repository("owner/repo"))
            .agents(new ReviewAgentSelection.All())
            .outputDirectory(Path.of("./reports"))
            .additionalAgentDirs(List.of())
            .githubToken("ghp_token")
            .parallelism(3)
            .noSummary(true)
            .reviewModel("review-model")
            .reportModel("report-model")
            .summaryModel("summary-model")
            .defaultModel("default-model")
            .trustTarget(false)
            .build();
        var target = ReviewTarget.gitHub("owner/repo");
        var resolvedModels = new ReviewModelConfigResolver.ResolvedModels("review-model", "report-model", "summary-model", "high");
        var agentConfigs = Map.of("code-quality", new AgentConfig("code-quality", "Code Quality", "review-model", "prompt", "instruction", "", List.of(), List.of()));
        var outputDirectory = Path.of("./reports/owner/repo");

        ReviewRequest request = factory.create(
            options,
            target,
            agentConfigs,
            outputDirectory,
            "2026-03-05-12-34-56",
            "ghp_token",
            resolvedModels
        );

        assertThat(request.target()).isEqualTo(target);
        assertThat(request.reasoningEffort()).isEqualTo("high");
        assertThat(request.invocationTimestamp()).isEqualTo("2026-03-05-12-34-56");
        assertThat(request.agents()).containsExactlyElementsOf(agentConfigs.values());
        assertThat(request.parallelism()).isEqualTo(3);
        assertThat(request.noSummary()).isTrue();
        assertThat(request.noSharedSession()).isFalse();
        assertThat(request.outputDir()).isEqualTo(outputDirectory);
        assertThat(request.githubToken()).isEqualTo("ghp_token");
        assertThat(request.rubberDuck()).isFalse();
    }
}
