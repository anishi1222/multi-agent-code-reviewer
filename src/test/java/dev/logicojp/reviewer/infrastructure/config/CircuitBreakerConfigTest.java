package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CircuitBreakerConfig")
class CircuitBreakerConfigTest {

    @Test
    @DisplayName("0以下の値はデフォルトに補正される")
    void defaultsForNonPositiveValues() {
        CircuitBreakerConfig config = new CircuitBreakerConfig(0, 0);

        assertThat(config.failureThreshold()).isEqualTo(8);
        assertThat(config.resetTimeoutMs()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("正の値はそのまま保持される")
    void keepsPositiveValues() {
        CircuitBreakerConfig config = new CircuitBreakerConfig(12, 45_000L);

        assertThat(config.failureThreshold()).isEqualTo(12);
        assertThat(config.resetTimeoutMs()).isEqualTo(45_000L);
    }
}
