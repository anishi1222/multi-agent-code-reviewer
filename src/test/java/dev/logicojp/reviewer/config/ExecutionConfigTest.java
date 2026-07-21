package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionConfig")
class ExecutionConfigTest {

    @Test
    @DisplayName("不正な値はデフォルト値に正規化される")
    void invalidValuesFallbackToDefaults() {
        ExecutionConfig config = ExecutionConfig.of(
            new ExecutionConfig.ConcurrencySettings(0),
            new ExecutionConfig.TimeoutSettings(0, 0, 0, 0, 0, 0),
            new ExecutionConfig.RetrySettings(-1),
            new ExecutionConfig.BufferSettings(0)
        );

        assertThat(config.parallelism()).isEqualTo(4);
        assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(10);
        assertThat(config.agentTimeoutMinutes()).isEqualTo(5);
        assertThat(config.idleTimeoutMinutes()).isEqualTo(ExecutionConfig.DEFAULT_IDLE_TIMEOUT_MINUTES);
        assertThat(config.skillTimeoutMinutes()).isEqualTo(5);
        assertThat(config.summaryTimeoutMinutes()).isEqualTo(5);
        assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(10);
        assertThat(config.maxRetries()).isEqualTo(ExecutionConfig.DEFAULT_MAX_RETRIES);
        assertThat(config.instructionBufferExtraCapacity())
            .isEqualTo(ExecutionConfig.DEFAULT_INSTRUCTION_BUFFER_EXTRA_CAPACITY);
    }

    @Test
    @DisplayName("正の値は保持される")
    void positiveValuesArePreserved() {
        ExecutionConfig config = new ExecutionConfig(
            new ExecutionConfig.ConcurrencySettings(8),
            new ExecutionConfig.TimeoutSettings(20, 15, 6, 10, 12, 30),
            new ExecutionConfig.RetrySettings(3),
            new ExecutionConfig.BufferSettings(64),
            true
        );

        assertThat(config.parallelism()).isEqualTo(8);
        assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(20);
        assertThat(config.agentTimeoutMinutes()).isEqualTo(15);
        assertThat(config.idleTimeoutMinutes()).isEqualTo(6);
        assertThat(config.skillTimeoutMinutes()).isEqualTo(10);
        assertThat(config.summaryTimeoutMinutes()).isEqualTo(12);
        assertThat(config.ghAuthTimeoutSeconds()).isEqualTo(30);
        assertThat(config.maxRetries()).isEqualTo(3);
        assertThat(config.instructionBufferExtraCapacity()).isEqualTo(64);
        assertThat(config.isGhAuthFallbackEnabled()).isTrue();
    }

    @Test
    @DisplayName("withParallelismは他の値を維持する")
    void withParallelismPreservesOtherValues() {
        ExecutionConfig original = ExecutionConfig.defaults();

        ExecutionConfig updated = original.withParallelism(8);

        assertThat(updated.parallelism()).isEqualTo(8);
        assertThat(updated.timeouts()).isEqualTo(original.timeouts());
        assertThat(updated.retry()).isEqualTo(original.retry());
        assertThat(updated.buffers()).isEqualTo(original.buffers());
        assertThat(original.parallelism()).isEqualTo(4);
    }

    @Test
    @DisplayName("gh authフォールバックを上書きできる")
    void canOverrideGhAuthFallback() {
        ExecutionConfig updated = ExecutionConfig.defaults().withGhAuthFallbackEnabled(true);

        assertThat(updated.isGhAuthFallbackEnabled()).isTrue();
    }

    @Test
    @DisplayName("builderはデフォルト値を起点に部分更新できる")
    void builderStartsFromDefaults() {
        ExecutionConfig config = ExecutionConfig.builder()
            .parallelism(6)
            .maxRetries(0)
            .build();

        assertThat(config.parallelism()).isEqualTo(6);
        assertThat(config.maxRetries()).isZero();
        assertThat(config.orchestratorTimeoutMinutes()).isEqualTo(10);
        assertThat(config.isGhAuthFallbackEnabled()).isFalse();
    }
}
