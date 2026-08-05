package dev.logicojp.reviewer.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewModelConfigResolver")
class ReviewModelConfigResolverTest {

    @Test
    @DisplayName("CLIのモデル指定で全モデルを上書きできる")
    void resolvesAllModelsFromCliOverrides() {
        var resolver = new ReviewModelConfigResolver("base-r", "base-p", "base-s", "high");

        ReviewModelConfigResolver.ResolvedModels resolved = resolver.resolve(
            parsedOptions("override-model", "override-model", "override-model", null)
        );

        assertThat(resolved.reviewModel()).isEqualTo("override-model");
        assertThat(resolved.reportModel()).isEqualTo("override-model");
        assertThat(resolved.summaryModel()).isEqualTo("override-model");
    }

    @Test
    @DisplayName("個別モデル指定は設定値より優先される")
    void explicitPerStageModelOverridesConfiguredModel() {
        var resolver = new ReviewModelConfigResolver("base-r", "base-p", "base-s", "high");

        ReviewModelConfigResolver.ResolvedModels resolved = resolver.resolve(
            parsedOptions("review-only", null, "summary-only", null)
        );

        assertThat(resolved.reviewModel()).isEqualTo("review-only");
        assertThat(resolved.reportModel()).isEqualTo("base-p");
        assertThat(resolved.summaryModel()).isEqualTo("summary-only");
    }

    private static ReviewOptions parsedOptions(String reviewModel,
                                               String reportModel,
                                               String summaryModel,
                                               String reasoningEffort) {
        return ReviewOptions.builder()
            .target(new ReviewTargetSelection.Repository("owner/repo"))
            .agents(new ReviewAgentSelection.All())
            .outputDirectory(Path.of("./reports"))
            .additionalAgentDirs(List.of())
            .parallelism(4)
            .noSummary(false)
            .reviewModel(reviewModel)
            .reportModel(reportModel)
            .summaryModel(summaryModel)
            .reasoningEffort(reasoningEffort)
            .trustTarget(false)
            .build();
    }
}
