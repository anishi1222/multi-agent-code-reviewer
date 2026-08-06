package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModelConfig")
class ModelConfigTest {

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("引数なしコンストラクタはデフォルトモデルを設定する")
        void noArgConstructorSetsDefaults() {
            ModelConfig config = new ModelConfig();

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.defaultModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("正準コンストラクタは全フィールドに同じモデルを設定できる")
        void canonicalConstructorCanSetAllModelsToSameValue() {
            String model = "gpt-4o";
            ModelConfig config = new ModelConfig(model, model, model, null, model);

            assertThat(config.reviewModel()).isEqualTo(model);
            assertThat(config.reportModel()).isEqualTo(model);
            assertThat(config.summaryModel()).isEqualTo(model);
            assertThat(config.defaultModel()).isEqualTo(model);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("3引数コンストラクタは各フィールドを個別に設定しデフォルトeffortを使用する")
        void canonicalConstructorSetsIndividualFields() {
            ModelConfig config = new ModelConfig("model-a", "model-b", "model-c", null, null);

            assertThat(config.reviewModel()).isEqualTo("model-a");
            assertThat(config.reportModel()).isEqualTo("model-b");
            assertThat(config.summaryModel()).isEqualTo("model-c");
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("4引数コンストラクタはreasoningEffortを個別に設定する")
        void canonicalConstructorSetsReasoningEffort() {
            ModelConfig config = new ModelConfig("model-a", "model-b", "model-c", "low", null);

            assertThat(config.reviewModel()).isEqualTo("model-a");
            assertThat(config.reportModel()).isEqualTo("model-b");
            assertThat(config.summaryModel()).isEqualTo("model-c");
            assertThat(config.reasoningEffort()).isEqualTo("low");
        }

        @Test
        @DisplayName("nullのフィールドはデフォルト値に変換される")
        void nullFieldsAreConvertedToDefaults() {
            ModelConfig config = new ModelConfig(null, null, null, null, null);

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("空白のみのフィールドはデフォルト値に変換される")
        void blankFieldsAreConvertedToDefaults() {
            ModelConfig config = new ModelConfig("  ", "\t", "\n", "  ", null);

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("空文字列のフィールドはデフォルト値に変換される")
        void emptyFieldsAreConvertedToDefaults() {
            ModelConfig config = new ModelConfig("", "", "", "", null);

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }
    }

    @Nested
    @DisplayName("isReasoningModel")
    class IsReasoningModel {

        @Test
        @DisplayName("Opusモデルはreasoningモデルと判定される")
        void opusModelIsReasoning() {
            assertThat(ModelConfig.isReasoningModel("claude-opus-4.6")).isTrue();
            assertThat(ModelConfig.isReasoningModel("claude-opus-4.6-fast")).isTrue();
            assertThat(ModelConfig.isReasoningModel("Claude-Opus-4.5")).isTrue();
        }

        @Test
        @DisplayName("o3モデルはreasoningモデルと判定される")
        void o3ModelIsReasoning() {
            assertThat(ModelConfig.isReasoningModel("o3")).isTrue();
            assertThat(ModelConfig.isReasoningModel("o3-mini")).isTrue();
        }

        @Test
        @DisplayName("o4-miniモデルはreasoningモデルと判定される")
        void o4MiniModelIsReasoning() {
            assertThat(ModelConfig.isReasoningModel("o4-mini")).isTrue();
        }

        @Test
        @DisplayName("非reasoningモデルはfalseを返す")
        void nonReasoningModelReturnsFalse() {
            assertThat(ModelConfig.isReasoningModel("claude-sonnet-4")).isFalse();
            assertThat(ModelConfig.isReasoningModel("gpt-4o")).isFalse();
            assertThat(ModelConfig.isReasoningModel("GPT-5.2-Codex")).isFalse();
        }

        @Test
        @DisplayName("nullの場合はfalseを返す")
        void nullReturnsFalse() {
            assertThat(ModelConfig.isReasoningModel(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("正準コンストラクタ")
    class CanonicalConstructorTests {

        @Test
        @DisplayName("引数なしコンストラクタはデフォルトモデルで構築する")
        void noArgConstructorBuildsWithDefaults() {
            ModelConfig config = new ModelConfig();

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reasoningEffort()).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("reviewModelを設定できる")
        void canSetReviewModel() {
            ModelConfig config = new ModelConfig("custom-review", null, null, null, null);

            assertThat(config.reviewModel()).isEqualTo("custom-review");
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("reportModelを設定できる")
        void canSetReportModel() {
            ModelConfig config = new ModelConfig(null, "custom-report", null, null, null);

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo("custom-report");
            assertThat(config.summaryModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("summaryModelを設定できる")
        void canSetSummaryModel() {
            ModelConfig config = new ModelConfig(null, null, "custom-summary", null, null);

            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reportModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.summaryModel()).isEqualTo("custom-summary");
        }

        @Test
        @DisplayName("defaultModelで全フィールドを一括設定できる")
        void defaultModelSetsUnsetFields() {
            ModelConfig config = new ModelConfig(null, null, null, null, "unified-model");

            assertThat(config.reviewModel()).isEqualTo("unified-model");
            assertThat(config.reportModel()).isEqualTo("unified-model");
            assertThat(config.summaryModel()).isEqualTo("unified-model");
            assertThat(config.defaultModel()).isEqualTo("unified-model");
        }

        @Test
        @DisplayName("複数フィールドを設定できる")
        void canSetMultipleFields() {
            ModelConfig config = new ModelConfig("review", "report", "summary", null, null);

            assertThat(config.reviewModel()).isEqualTo("review");
            assertThat(config.reportModel()).isEqualTo("report");
            assertThat(config.summaryModel()).isEqualTo("summary");
        }

        @Test
        @DisplayName("reasoningEffortを設定できる")
        void canSetReasoningEffort() {
            ModelConfig config = new ModelConfig(null, null, null, "low", null);

            assertThat(config.reasoningEffort()).isEqualTo("low");
        }
    }

    @Nested
    @DisplayName("defaultModel")
    class DefaultModelTests {

        @Test
        @DisplayName("defaultModelがYAMLで設定された場合、未指定のモデルはdefaultModelにフォールバックする")
        void defaultModelFallsBackForUnsetModels() {
            ModelConfig config = new ModelConfig(null, null, null, null, "custom-fallback");

            assertThat(config.defaultModel()).isEqualTo("custom-fallback");
            assertThat(config.reviewModel()).isEqualTo("custom-fallback");
            assertThat(config.reportModel()).isEqualTo("custom-fallback");
            assertThat(config.summaryModel()).isEqualTo("custom-fallback");
        }

        @Test
        @DisplayName("defaultModelが未設定の場合はDEFAULT_MODELにフォールバックする")
        void nullDefaultModelFallsBackToConstant() {
            ModelConfig config = new ModelConfig(null, null, null, null, null);

            assertThat(config.defaultModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
            assertThat(config.reviewModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("個別モデルが設定されている場合はdefaultModelより優先される")
        void individualModelsOverrideDefaultModel() {
            ModelConfig config = new ModelConfig("review-x", null, null, null, "custom-fallback");

            assertThat(config.reviewModel()).isEqualTo("review-x");
            assertThat(config.reportModel()).isEqualTo("custom-fallback");
            assertThat(config.summaryModel()).isEqualTo("custom-fallback");
        }

        @Test
        @DisplayName("引数なしコンストラクタのdefaultModelはDEFAULT_MODELと等しい")
        void noArgConstructorDefaultModel() {
            ModelConfig config = new ModelConfig();

            assertThat(config.defaultModel()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toStringはモデル名とreasoningEffortとdefaultModelを含む")
        void toStringContainsModelNamesAndEffort() {
            ModelConfig config = new ModelConfig("r-model", "p-model", "s-model", "medium", null);

            String result = config.toString();

            assertThat(result).contains("r-model");
            assertThat(result).contains("p-model");
            assertThat(result).contains("s-model");
            assertThat(result).contains("medium");
            assertThat(result).contains(ModelConfig.DEFAULT_MODEL);
        }
    }

    @Nested
    @DisplayName("resolveReasoningEffort")
    class ResolveReasoningEffort {

        @Test
        @DisplayName("推論モデルの場合は設定されたeffortを返す")
        void reasoningModelReturnsConfiguredEffort() {
            String effort = ModelConfig.resolveReasoningEffort("claude-opus-4.6", "medium");
            assertThat(effort).isEqualTo("medium");
        }

        @Test
        @DisplayName("推論モデルでeffortがnullの場合はデフォルトeffortを返す")
        void reasoningModelWithNullEffortReturnsDefault() {
            String effort = ModelConfig.resolveReasoningEffort("o3", null);
            assertThat(effort).isEqualTo(ModelConfig.DEFAULT_REASONING_EFFORT);
        }

        @Test
        @DisplayName("非推論モデルの場合はnullを返す")
        void nonReasoningModelReturnsNull() {
            String effort = ModelConfig.resolveReasoningEffort("claude-sonnet-4", "high");
            assertThat(effort).isNull();
        }

        @Test
        @DisplayName("nullモデルの場合はnullを返す")
        void nullModelReturnsNull() {
            String effort = ModelConfig.resolveReasoningEffort(null, "high");
            assertThat(effort).isNull();
        }
    }
}
