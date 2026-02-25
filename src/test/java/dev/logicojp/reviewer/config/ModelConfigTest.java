package dev.logicojp.reviewer.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


@DisplayName("ModelConfig")
class ModelConfigTest {

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("引数なしコンストラクタはデフォルトモデルを設定する")
        void noArgConstructorSetsDefaults() {
            var config = new ModelConfig();

            Assertions.assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.defaultModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("nullのフィールドはデフォルト値に変換される")
        void nullFieldsAreConvertedToDefaults() {
            var config = new ModelConfig(null, null, null, null, null);

            Assertions.assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("空白のみのフィールドはデフォルト値に変換される")
        void blankFieldsAreConvertedToDefaults() {
            var config = new ModelConfig("  ", "\t", "\n", "  ", null);

            Assertions.assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("空文字列のフィールドはデフォルト値に変換される")
        void emptyFieldsAreConvertedToDefaults() {
            var config = new ModelConfig("", "", "", "", null);

            Assertions.assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("各フィールドを個別に設定する")
        void canonicalConstructorSetsIndividualFields() {
            var config = new ModelConfig("model-a", "model-b", "model-c", null, null);

            Assertions.assertThat(config.reviewModel()).isEqualTo("model-a");
            Assertions.assertThat(config.reportModel()).isEqualTo("model-b");
            Assertions.assertThat(config.summaryModel()).isEqualTo("model-c");
            Assertions.assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("reasoningEffortを個別に設定する")
        void canonicalConstructorSetsReasoningEffort() {
            var config = new ModelConfig("model-a", "model-b", "model-c", "low", null);

            Assertions.assertThat(config.reasoningEffort()).isEqualTo("low");
        }
    }

    @Nested
    @DisplayName("defaultModel")
    class DefaultModelTests {

        @Test
        @DisplayName("defaultModelがYAMLで設定された場合、未指定のモデルはdefaultModelにフォールバックする")
        void defaultModelFallsBackForUnsetModels() {
            var config = new ModelConfig(null, null, null, null, "custom-fallback");

            Assertions.assertThat(config.defaultModel()).isEqualTo("custom-fallback");
            Assertions.assertThat(config.reviewModel()).isEqualTo("custom-fallback");
            Assertions.assertThat(config.reportModel()).isEqualTo("custom-fallback");
            Assertions.assertThat(config.summaryModel()).isEqualTo("custom-fallback");
        }

        @Test
        @DisplayName("defaultModelが未設定の場合はDEFAULT_MODELにフォールバックする")
        void nullDefaultModelFallsBackToConstant() {
            var config = new ModelConfig(null, null, null, null, null);

            Assertions.assertThat(config.defaultModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("個別モデルが設定されている場合はdefaultModelより優先される")
        void individualModelsOverrideDefaultModel() {
            var config = new ModelConfig("review-x", null, null, null, "custom-fallback");

            Assertions.assertThat(config.reviewModel()).isEqualTo("review-x");
            Assertions.assertThat(config.reportModel()).isEqualTo("custom-fallback");
            Assertions.assertThat(config.summaryModel()).isEqualTo("custom-fallback");
        }
    }

    @Nested
    @DisplayName("isReasoningModel")
    class IsReasoningModel {

        @Test
        @DisplayName("Opusモデルはreasoningモデルと判定される")
        void opusModelIsReasoning() {
            Assertions.assertThat(ModelConfig.isReasoningModel("claude-opus-4.6")).isTrue();
            Assertions.assertThat(ModelConfig.isReasoningModel("claude-opus-4.6-fast")).isTrue();
            Assertions.assertThat(ModelConfig.isReasoningModel("Claude-Opus-4.5")).isTrue();
        }

        @Test
        @DisplayName("o3モデルはreasoningモデルと判定される")
        void o3ModelIsReasoning() {
            Assertions.assertThat(ModelConfig.isReasoningModel("o3")).isTrue();
            Assertions.assertThat(ModelConfig.isReasoningModel("o3-mini")).isTrue();
        }

        @Test
        @DisplayName("o4-miniモデルはreasoningモデルと判定される")
        void o4MiniModelIsReasoning() {
            Assertions.assertThat(ModelConfig.isReasoningModel("o4-mini")).isTrue();
        }

        @Test
        @DisplayName("非reasoningモデルはfalseを返す")
        void nonReasoningModelReturnsFalse() {
            Assertions.assertThat(ModelConfig.isReasoningModel("claude-sonnet-4")).isFalse();
            Assertions.assertThat(ModelConfig.isReasoningModel("gpt-4o")).isFalse();
        }

        @Test
        @DisplayName("nullの場合はfalseを返す")
        void nullReturnsFalse() {
            Assertions.assertThat(ModelConfig.isReasoningModel(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveReasoningEffort")
    class ResolveReasoningEffort {

        @Test
        @DisplayName("推論モデルの場合は設定されたeffortを返す")
        void reasoningModelReturnsConfiguredEffort() {
            Assertions.assertThat(ModelConfig.resolveReasoningEffort("claude-opus-4.6", "medium"))
                .isEqualTo("medium");
        }

        @Test
        @DisplayName("推論モデルでeffortがnullの場合はデフォルトeffortを返す")
        void reasoningModelWithNullEffortReturnsDefault() {
            Assertions.assertThat(ModelConfig.resolveReasoningEffort("o3", null))
                .isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("非推論モデルの場合はnullを返す")
        void nonReasoningModelReturnsNull() {
            Assertions.assertThat(ModelConfig.resolveReasoningEffort("claude-sonnet-4", "high")).isNull();
        }

        @Test
        @DisplayName("nullモデルの場合はnullを返す")
        void nullModelReturnsNull() {
            Assertions.assertThat(ModelConfig.resolveReasoningEffort(null, "high")).isNull();
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("デフォルトのビルダーはデフォルトモデルで構築する")
        void defaultBuilderBuildsWithDefaults() {
            var config = ModelConfig.builder().build();

            Assertions.assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            Assertions.assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("allModelsで全フィールドを一括設定できる")
        void allModelsSetsAllFields() {
            var config = ModelConfig.builder().allModels("unified-model").build();

            Assertions.assertThat(config.reviewModel()).isEqualTo("unified-model");
            Assertions.assertThat(config.reportModel()).isEqualTo("unified-model");
            Assertions.assertThat(config.summaryModel()).isEqualTo("unified-model");
            Assertions.assertThat(config.defaultModel()).isEqualTo("unified-model");
        }

        @Test
        @DisplayName("メソッドチェーンで複数フィールドを設定できる")
        void canChainMultipleMethods() {
            var config = ModelConfig.builder()
                .reviewModel("review")
                .reportModel("report")
                .summaryModel("summary")
                .reasoningEffort("low")
                .build();

            Assertions.assertThat(config.reviewModel()).isEqualTo("review");
            Assertions.assertThat(config.reportModel()).isEqualTo("report");
            Assertions.assertThat(config.summaryModel()).isEqualTo("summary");
            Assertions.assertThat(config.reasoningEffort()).isEqualTo("low");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toStringはモデル名とreasoningEffortを含む")
        void toStringContainsModelNames() {
            var config = new ModelConfig("r-model", "p-model", "s-model", "medium", null);
            var result = config.toString();

            Assertions.assertThat(result).contains("r-model", "p-model", "s-model", "medium");
        }
    }
}
