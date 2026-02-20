package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResilienceConfig")
class ResilienceConfigTest {

    @Test
    @DisplayName("null入力時に各操作設定へデフォルト値を補完する")
    void defaultsWhenNull() {
        var config = new ResilienceConfig(null, null, null);

        assertThat(config.review().failureThreshold()).isEqualTo(5);
        assertThat(config.review().openDurationSeconds()).isEqualTo(30);
        assertThat(config.review().maxAttempts()).isEqualTo(3);

        assertThat(config.summary().failureThreshold()).isEqualTo(3);
        assertThat(config.summary().openDurationSeconds()).isEqualTo(20);

        assertThat(config.skill().failureThreshold()).isEqualTo(3);
        assertThat(config.skill().openDurationSeconds()).isEqualTo(20);
    }

    @Test
    @DisplayName("backoff-max-msがbase未満でもbase値へ補正する")
    void normalizesBackoffRange() {
        var settings = new ResilienceConfig.OperationSettings(2, 10, 2, 500, 100);

        assertThat(settings.backoffBaseMs()).isEqualTo(500);
        assertThat(settings.backoffMaxMs()).isEqualTo(500);
    }
}
