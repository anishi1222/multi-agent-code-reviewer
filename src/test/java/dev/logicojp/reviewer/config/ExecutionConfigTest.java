package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionConfig")
class ExecutionConfigTest {

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {
        @Test
        @DisplayName("ghAuthFallbackEnabledのデフォルトはfalse")
        void ghAuthFallbackDefaultIsFalse() {
            ExecutionConfig config = ExecutionConfig.defaults();
            assertThat(config.isGhAuthFallbackEnabled()).isFalse();
        }


        @Test
        @DisplayName("parallelismが0以下の場合は4に設定される")
        void parallelismZeroDefaultsToFour() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(0, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.parallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("parallelismが負数の場合は4に設定される")
        void parallelismNegativeDefaultsToFour() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(-1, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.parallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("orchestratorTimeoutMinutesが0以下の場合は10に設定される")
        void orchestratorTimeoutZeroDefaultsToTen() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 0, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(10);
        }

        @Test
        @DisplayName("orchestratorTimeoutMinutesが負数の場合は10に設定される")
        void orchestratorTimeoutNegativeDefaultsToTen() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, -5, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(10);
        }

        @Test
        @DisplayName("agentTimeoutMinutesが0以下の場合は5に設定される")
        void agentTimeoutZeroDefaultsToFive() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 0, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("agentTimeoutMinutesが負数の場合は5に設定される")
        void agentTimeoutNegativeDefaultsToFive() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, -3, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("idleTimeoutMinutesが0以下の場合はデフォルト値に設定される")
        void idleTimeoutZeroDefaultsToDefault() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 0, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.idleTimeoutMinutes()).isEqualTo(ExecutionConfig.DEFAULT_IDLE_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("idleTimeoutMinutesが負数の場合はデフォルト値に設定される")
        void idleTimeoutNegativeDefaultsToDefault() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, -3, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.idleTimeoutMinutes()).isEqualTo(ExecutionConfig.DEFAULT_IDLE_TIMEOUT_MINUTES);
        }

        @Test
        @DisplayName("skillTimeoutMinutesが0以下の場合は5に設定される")
        void skillTimeoutZeroDefaultsToFive() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 0, 5, 10, 2, 0, 0, 0);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("skillTimeoutMinutesが負数の場合は5に設定される")
        void skillTimeoutNegativeDefaultsToFive() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, -2, 5, 10, 2, 0, 0, 0);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("summaryTimeoutMinutesが0以下の場合は5に設定される")
        void summaryTimeoutZeroDefaultsToFive() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 0, 10, 2, 0, 0, 0);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("summaryTimeoutMinutesが負数の場合は5に設定される")
        void summaryTimeoutNegativeDefaultsToFive() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, -1, 10, 2, 0, 0, 0);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("ghAuthTimeoutSecondsが0以下の場合は10に設定される")
        void ghAuthTimeoutZeroDefaultsToTen() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 0, 2, 0, 0, 0);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("ghAuthTimeoutSecondsが負数の場合は10に設定される")
        void ghAuthTimeoutNegativeDefaultsToTen() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, -3, 2, 0, 0, 0);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxRetriesが負数の場合はデフォルト値に設定される")
        void maxRetriesNegativeDefaultsToDefault() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, -1, 0, 0, 0);
            assertThat(config.maxRetries()).isEqualTo(ExecutionConfig.DEFAULT_MAX_RETRIES);
        }

        @Test
        @DisplayName("reviewPassesが0以下の場合はデフォルト値に設定される")
        void reviewPassesZeroDefaultsToDefault() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 0, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.reviewPasses()).isEqualTo(ExecutionConfig.DEFAULT_REVIEW_PASSES);
        }

        @Test
        @DisplayName("reviewPassesが負数の場合はデフォルト値に設定される")
        void reviewPassesNegativeDefaultsToDefault() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, -1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.reviewPasses()).isEqualTo(ExecutionConfig.DEFAULT_REVIEW_PASSES);
        }
    }

    @Nested
    @DisplayName("コンストラクタ - 正常値")
    class ValidValues {

        @Test
        @DisplayName("正の値が指定された場合はそのまま保持される")
        void positiveValuesArePreserved() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(8, 3, 20, 15, 5, 10, 12, 30, 3, 0, 0, 0);

            assertThat(config.parallelism()).isEqualTo(8);
            assertThat(config.reviewPasses()).isEqualTo(3);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(20);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(15);
            assertThat(config.idleTimeoutMinutes()).isEqualTo(5);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(10);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(12);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(30);
            assertThat(config.maxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("parallelismが1の場合はそのまま保持される")
        void parallelismOneIsPreserved() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(1, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            assertThat(config.parallelism()).isEqualTo(1);
        }

        @Test
        @DisplayName("タイムアウトが1の場合はそのまま保持される")
        void timeoutOneIsPreserved() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 1, 1, 1, 1, 1, 1, 2, 0, 0, 0);

            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(1);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(1);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(1);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(1);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("大きな値も正しく保持される")
        void largeValuesArePreserved() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(100, 5, 1000, 500, 50, 300, 200, 600, 10, 0, 0, 0);

            assertThat(config.parallelism()).isEqualTo(100);
            assertThat(config.reviewPasses()).isEqualTo(5);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(1000);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(500);
            assertThat(config.idleTimeoutMinutes()).isEqualTo(50);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(300);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(200);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(600);
            assertThat(config.maxRetries()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxRetriesが0の場合はそのまま保持される（リトライなし）")
        void maxRetriesZeroIsPreserved() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 0, 0, 0, 0);
            assertThat(config.maxRetries()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("レコードの等価性")
    class RecordEquality {

        @Test
        @DisplayName("同じ値を持つレコードは等価である")
        void sameValuesAreEqual() {
            ExecutionConfig config1 = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            ExecutionConfig config2 = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);

            assertThat(config1).isEqualTo(config2);
            assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        }

        @Test
        @DisplayName("異なる値を持つレコードは等価でない")
        void differentValuesAreNotEqual() {
            ExecutionConfig config1 = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            ExecutionConfig config2 = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(8, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);

            assertThat(config1).isNotEqualTo(config2);
        }
    }

    @Nested
    @DisplayName("withParallelism")
    class WithParallelism {

        @Test
        @DisplayName("parallelismのみを変更した新しいインスタンスを返す")
        void changesOnlyParallelism() {
            ExecutionConfig original = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            ExecutionConfig updated = original.withParallelism(8);

            assertThat(updated.parallelism()).isEqualTo(8);
            assertThat(updated.orchestratorTimeoutMinutes()).isEqualTo(original.orchestratorTimeoutMinutes());
            assertThat(updated.agentTimeoutMinutes()).isEqualTo(original.agentTimeoutMinutes());
            assertThat(updated.idleTimeoutMinutes()).isEqualTo(original.idleTimeoutMinutes());
            assertThat(updated.skillTimeoutMinutes()).isEqualTo(original.skillTimeoutMinutes());
            assertThat(updated.summaryTimeoutMinutes()).isEqualTo(original.summaryTimeoutMinutes());
            assertThat(updated.ghAuthTimeoutSeconds()).isEqualTo(original.ghAuthTimeoutSeconds());
            assertThat(updated.maxRetries()).isEqualTo(original.maxRetries());
        }

        @Test
        @DisplayName("元のインスタンスは変更されない")
        void doesNotMutateOriginal() {
            ExecutionConfig original = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            original.withParallelism(16);
            assertThat(original.parallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("shared-sessionフラグを上書きできる")
        void canOverrideSharedSessionFlag() {
            ExecutionConfig original = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);

            ExecutionConfig updated = original.withSharedSessionEnabled(false);

            assertThat(original.isSharedSessionEnabled()).isTrue();
            assertThat(updated.isSharedSessionEnabled()).isFalse();
        }

        @Test
        @DisplayName("gh-authフォールバックフラグを上書きできる")
        void canOverrideGhAuthFallbackFlag() {
            ExecutionConfig original = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);

            ExecutionConfig updated = original.withGhAuthFallbackEnabled(true);

            assertThat(original.isGhAuthFallbackEnabled()).isFalse();
            assertThat(updated.isGhAuthFallbackEnabled()).isTrue();
        }

        @Test
        @DisplayName("0以下の値はデフォルト値に正規化される")
        void invalidValueIsNormalized() {
            ExecutionConfig original = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(4, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);
            ExecutionConfig updated = original.withParallelism(0);
            assertThat(updated.parallelism()).isEqualTo(4); // default
        }
    }

    @Nested
    @DisplayName("グルーピング設定")
    class GroupedSettings {

        @Test
        @DisplayName("ofファクトリはグルーピング設定からExecutionConfigを生成できる")
        void createsConfigFromGroupedSettings() {
            ExecutionConfig config = ExecutionConfig.of(
                new ExecutionConfig.ConcurrencySettings(3, 2),
                new ExecutionConfig.TimeoutSettings(20, 10, 6, 8, 9, 30),
                new ExecutionConfig.RetrySettings(4),
                new ExecutionConfig.BufferSettings(8192, 1024, 64)
            );

            assertThat(config.parallelism()).isEqualTo(3);
            assertThat(config.reviewPasses()).isEqualTo(2);
            assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(20);
            assertThat(config.agentTimeoutMinutes()).isEqualTo(10);
            assertThat(config.idleTimeoutMinutes()).isEqualTo(6);
            assertThat(config.skillTimeoutMinutes()).isEqualTo(8);
            assertThat(config.summaryTimeoutMinutes()).isEqualTo(9);
            assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(30);
            assertThat(config.maxRetries()).isEqualTo(4);
            assertThat(config.maxAccumulatedSize()).isEqualTo(8192);
            assertThat(config.initialAccumulatedCapacity()).isEqualTo(1024);
            assertThat(config.instructionBufferExtraCapacity()).isEqualTo(64);
        }

        @Test
        @DisplayName("group accessorは現在値を返す")
        void exposesGroupedAccessors() {
            ExecutionConfig config = dev.logicojp.reviewer.testutil.ExecutionConfigFixtures.config(5, 3, 21, 11, 7, 9, 10, 40, 2, 4096, 512, 48);

            assertThat(config.concurrency().parallelism()).isEqualTo(5);
            assertThat(config.concurrency().reviewPasses()).isEqualTo(3);
            assertThat(config.timeouts().orchestratorTimeoutMinutes()).isEqualTo(21);
            assertThat(config.retry().maxRetries()).isEqualTo(2);
            assertThat(config.buffers().maxAccumulatedSize()).isEqualTo(4096);
        }

        @Test
        @DisplayName("ofファクトリでshared-sessionフラグを指定できる")
        void createsConfigWithSharedSessionFlag() {
            ExecutionConfig config = ExecutionConfig.of(
                new ExecutionConfig.ConcurrencySettings(3, 2),
                new ExecutionConfig.TimeoutSettings(20, 10, 6, 8, 9, 30),
                new ExecutionConfig.RetrySettings(4),
                new ExecutionConfig.BufferSettings(8192, 1024, 64),
                false
            );

            assertThat(config.isSharedSessionEnabled()).isFalse();
        }

        @Test
        @DisplayName("ofファクトリでgh-authフォールバックフラグを指定できる")
        void createsConfigWithGhAuthFallbackFlag() {
            ExecutionConfig config = ExecutionConfig.of(
                new ExecutionConfig.ConcurrencySettings(3, 2),
                new ExecutionConfig.TimeoutSettings(20, 10, 6, 8, 9, 30),
                new ExecutionConfig.RetrySettings(4),
                new ExecutionConfig.BufferSettings(8192, 1024, 64),
                false,
                true
            );

            assertThat(config.isSharedSessionEnabled()).isFalse();
            assertThat(config.isGhAuthFallbackEnabled()).isTrue();
        }
    }
}
