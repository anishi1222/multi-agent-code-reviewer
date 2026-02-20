package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionConfig")
class ExecutionConfigTest {

    private static final ExecutionConfig.SummarySettings DEFAULT_SUMMARY =
        new ExecutionConfig.SummarySettings(0, 0, 0, 0, 0, 0);

    private ExecutionConfig create(int parallelism, int reviewPasses,
                                   long orchestratorTimeout, long agentTimeout,
                                   long idleTimeout, long skillTimeout,
                                   long summaryTimeout, long ghAuthTimeout,
                                   int maxRetries) {
        return new ExecutionConfig(parallelism, reviewPasses, orchestratorTimeout,
            agentTimeout, idleTimeout, skillTimeout, summaryTimeout,
            ghAuthTimeout, maxRetries, 0, 0, 0, null, DEFAULT_SUMMARY);
    }

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("parallelismが0以下の場合はデフォルトに設定される")
        void parallelismZeroDefaultsToFour() {
            var config = create(0, 1, 10, 5, 5, 5, 5, 10, 2);
            assertThat(config.parallelism()).isEqualTo(ExecutionConfig.DEFAULT_PARALLELISM);
        }

        @Test
        @DisplayName("parallelismが負数の場合はデフォルトに設定される")
        void parallelismNegativeDefaultsToFour() {
            var config = create(-1, 1, 10, 5, 5, 5, 5, 10, 2);
            assertThat(config.parallelism()).isEqualTo(ExecutionConfig.DEFAULT_PARALLELISM);
        }

        @Test
        @DisplayName("orchestratorTimeoutMinutesが0以下の場合はデフォルトに設定される")
        void orchestratorTimeoutDefaultsToTen() {
            var config = create(4, 1, 0, 5, 5, 5, 5, 10, 2);
            assertThat(config.orchestratorTimeoutMinutes())
                .isEqualTo(ExecutionConfig.DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("agentTimeoutMinutesが0以下の場合はデフォルトに設定される")
        void agentTimeoutDefaultsToFive() {
            var config = create(4, 1, 10, 0, 5, 5, 5, 10, 2);
            assertThat(config.agentTimeoutMinutes())
                .isEqualTo(ExecutionConfig.DEFAULT_AGENT_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("idleTimeoutMinutesが0以下の場合はデフォルトに設定される")
        void idleTimeoutDefaultsToDefault() {
            var config = create(4, 1, 10, 5, 0, 5, 5, 10, 2);
            assertThat(config.idleTimeoutMinutes())
                .isEqualTo(ExecutionConfig.DEFAULT_IDLE_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("skillTimeoutMinutesが0以下の場合はデフォルトに設定される")
        void skillTimeoutDefaultsToFive() {
            var config = create(4, 1, 10, 5, 5, 0, 5, 10, 2);
            assertThat(config.skillTimeoutMinutes())
                .isEqualTo(ExecutionConfig.DEFAULT_SKILL_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("summaryTimeoutMinutesが0以下の場合はデフォルトに設定される")
        void summaryTimeoutDefaultsToFive() {
            var config = create(4, 1, 10, 5, 5, 5, 0, 10, 2);
            assertThat(config.summaryTimeoutMinutes())
                .isEqualTo(ExecutionConfig.DEFAULT_SUMMARY_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("ghAuthTimeoutSecondsが0以下の場合はデフォルトに設定される")
        void ghAuthTimeoutDefaultsToTen() {
            var config = create(4, 1, 10, 5, 5, 5, 5, 0, 2);
            assertThat(config.ghAuthTimeoutSeconds())
                .isEqualTo(ExecutionConfig.DEFAULT_GH_AUTH_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("maxRetriesが負数の場合はデフォルトに設定される")
        void maxRetriesNegativeDefaultsToDefault() {
            var config = create(4, 1, 10, 5, 5, 5, 5, 10, -1);
            assertThat(config.maxRetries()).isEqualTo(ExecutionConfig.DEFAULT_MAX_RETRIES);
        }

        @Test
        @DisplayName("reviewPassesが0以下の場合はデフォルトに設定される")
        void reviewPassesZeroDefaultsToDefault() {
            var config = create(4, 0, 10, 5, 5, 5, 5, 10, 2);
            assertThat(config.reviewPasses()).isEqualTo(ExecutionConfig.DEFAULT_REVIEW_PASSES);
        }

        @Test
        @DisplayName("summaryがnullの場合はデフォルトSummarySettingsが使われる")
        void nullSummaryDefaultsToNewInstance() {
            var config = new ExecutionConfig(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0, null, null);
            assertThat(config.summary()).isNotNull();
            assertThat(config.summary().maxContentPerAgent())
                .isEqualTo(ExecutionConfig.SummarySettings.DEFAULT_MAX_CONTENT_PER_AGENT);
        }

        @Test
        @DisplayName("checkpointDirectoryがnull/空の場合はデフォルトに設定される")
        void checkpointDirectoryDefaultsWhenBlank() {
            var nullConfig = new ExecutionConfig(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0, null, DEFAULT_SUMMARY);
            var blankConfig = new ExecutionConfig(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0, "  ", DEFAULT_SUMMARY);

            assertThat(nullConfig.checkpointDirectory()).isEqualTo(ExecutionConfig.DEFAULT_CHECKPOINT_DIRECTORY);
            assertThat(blankConfig.checkpointDirectory()).isEqualTo(ExecutionConfig.DEFAULT_CHECKPOINT_DIRECTORY);
        }
    }

    @Nested
    @DisplayName("コンストラクタ - 正常値")
    class ValidValues {

        @Test
        @DisplayName("正の値が指定された場合はそのまま保持される")
        void positiveValuesArePreserved() {
            var config = new ExecutionConfig(8, 3, 20, 15, 5, 10, 12, 30, 3,
                4096, 1024, 64, "./tmp/checkpoints", DEFAULT_SUMMARY);

            assertThat(config.parallelism()).isEqualTo(8);
            assertThat(config.reviewPasses()).isEqualTo(3);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(20);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(15);
            assertThat(config.idleTimeoutMinutes()).isEqualTo(5);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(10);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(12);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(30);
            assertThat(config.maxRetries()).isEqualTo(3);
            assertThat(config.checkpointDirectory()).isEqualTo("./tmp/checkpoints");
        }

        @Test
        @DisplayName("maxRetriesが0の場合はそのまま保持される（リトライなし）")
        void maxRetriesZeroIsPreserved() {
            var config = create(4, 1, 10, 5, 5, 5, 5, 10, 0);
            assertThat(config.maxRetries()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("レコードの等価性")
    class RecordEquality {

        @Test
        @DisplayName("同じ値を持つレコードは等価である")
        void sameValuesAreEqual() {
            var config1 = create(4, 1, 10, 5, 5, 5, 5, 10, 2);
            var config2 = create(4, 1, 10, 5, 5, 5, 5, 10, 2);

            assertThat(config1).isEqualTo(config2);
            assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        }

        @Test
        @DisplayName("異なる値を持つレコードは等価でない")
        void differentValuesAreNotEqual() {
            var config1 = create(4, 1, 10, 5, 5, 5, 5, 10, 2);
            var config2 = create(8, 1, 10, 5, 5, 5, 5, 10, 2);

            assertThat(config1).isNotEqualTo(config2);
        }
    }

    @Nested
    @DisplayName("withParallelism")
    class WithParallelism {

        @Test
        @DisplayName("parallelismのみを変更した新しいインスタンスを返す")
        void changesOnlyParallelism() {
            var original = create(4, 1, 10, 5, 5, 5, 5, 10, 2);
            var updated = original.withParallelism(8);

            assertThat(updated.parallelism()).isEqualTo(8);
            assertThat(updated.orchestratorTimeoutMinutes()).isEqualTo(original.orchestratorTimeoutMinutes());
            assertThat(updated.agentTimeoutMinutes()).isEqualTo(original.agentTimeoutMinutes());
            assertThat(updated.maxRetries()).isEqualTo(original.maxRetries());
        }

        @Test
        @DisplayName("元のインスタンスは変更されない")
        void doesNotMutateOriginal() {
            var original = create(4, 1, 10, 5, 5, 5, 5, 10, 2);
            original.withParallelism(16);
            assertThat(original.parallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("0以下の値はデフォルト値に正規化される")
        void invalidValueIsNormalized() {
            var original = create(4, 1, 10, 5, 5, 5, 5, 10, 2);
            var updated = original.withParallelism(0);
            assertThat(updated.parallelism()).isEqualTo(ExecutionConfig.DEFAULT_PARALLELISM);
        }
    }

    @Nested
    @DisplayName("SummarySettings")
    class SummarySettingsTests {

        @Test
        @DisplayName("0以下の値はデフォルトに設定される")
        void nonPositiveValuesDefaulted() {
            var settings = new ExecutionConfig.SummarySettings(0, 0, 0, 0, 0, 0);

            assertThat(settings.maxContentPerAgent())
                .isEqualTo(ExecutionConfig.SummarySettings.DEFAULT_MAX_CONTENT_PER_AGENT);
            assertThat(settings.maxTotalPromptContent())
                .isEqualTo(ExecutionConfig.SummarySettings.DEFAULT_MAX_TOTAL_PROMPT_CONTENT);
            assertThat(settings.fallbackExcerptLength())
                .isEqualTo(ExecutionConfig.SummarySettings.DEFAULT_FALLBACK_EXCERPT_LENGTH);
        }

        @Test
        @DisplayName("正の値はそのまま保持される")
        void positiveValuesPreserved() {
            var settings = new ExecutionConfig.SummarySettings(100, 200, 50, 300, 64, 5);

            assertThat(settings.maxContentPerAgent()).isEqualTo(100);
            assertThat(settings.maxTotalPromptContent()).isEqualTo(200);
            assertThat(settings.fallbackExcerptLength()).isEqualTo(50);
            assertThat(settings.averageResultContentEstimate()).isEqualTo(300);
            assertThat(settings.initialBufferMargin()).isEqualTo(64);
            assertThat(settings.excerptNormalizationMultiplier()).isEqualTo(5);
        }
    }
}
