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
        var resolver = new ReviewModelConfigResolver(() -> defaultPlan());

        ReviewModelConfigResolver.ResolvedModels resolved = resolver.resolve(
            parsedOptions(null, null, null, "override-model", null)
        );

        assertThat(resolved.reviewModel()).isEqualTo("override-model");
        assertThat(resolved.reportModel()).isEqualTo("override-model");
        assertThat(resolved.summaryModel()).isEqualTo("override-model");
    }

    @Test
    @DisplayName("個別モデル指定は--modelより優先される")
    void explicitPerStageModelOverridesCommonCliModel() {
        var resolver = new ReviewModelConfigResolver(() -> defaultPlan());

        ReviewModelConfigResolver.ResolvedModels resolved = resolver.resolve(
            parsedOptions("review-only", null, "summary-only", "all-stages", null)
        );

        assertThat(resolved.reviewModel()).isEqualTo("review-only");
        assertThat(resolved.reportModel()).isEqualTo("all-stages");
        assertThat(resolved.summaryModel()).isEqualTo("summary-only");
    }

    @Test
    @DisplayName("CLI指定が無い場合はplanの実効モデル既定値を使う")
    void usesEffectivePlanDefaultsWhenCliHasNoOverride() {
        var resolver = new ReviewModelConfigResolver(() -> defaultPlan());

        ReviewModelConfigResolver.ResolvedModels resolved =
            resolver.resolve(parsedOptions(null, null, null, null, null));

        assertThat(resolved)
            .extracting(
                ReviewModelConfigResolver.ResolvedModels::reviewModel,
                ReviewModelConfigResolver.ResolvedModels::reportModel,
                ReviewModelConfigResolver.ResolvedModels::summaryModel,
                ReviewModelConfigResolver.ResolvedModels::reasoningEffort)
            .containsExactly("base-r", "base-p", "base-s", "high");
    }

    private static ReviewOptions parsedOptions(String reviewModel,
                                               String reportModel,
                                               String summaryModel,
                                               String defaultModel,
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
            .defaultModel(defaultModel)
            .reasoningEffort(reasoningEffort)
            .trustTarget(false)
            .build();
    }

    private static dev.logicojp.reviewer.application.port.inbound.ReviewPlan defaultPlan() {
        return new dev.logicojp.reviewer.application.port.inbound.ReviewPlan(
            1, 4, "base-r", "base-p", "base-s", "high");
    }
}
