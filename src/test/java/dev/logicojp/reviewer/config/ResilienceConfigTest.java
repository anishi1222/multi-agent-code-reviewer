package dev.logicojp.reviewer.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("ResilienceConfig")
class ResilienceConfigTest {

    @Test
    @DisplayName("null入力時に各操作設定へデフォルト値を補完する")
    void defaultsWhenNull() {
        var config = new ResilienceConfig(null, null, null);

        Assertions.assertThat(config.review().failureThreshold()).isEqualTo(5);
        Assertions.assertThat(config.review().openDurationSeconds()).isEqualTo(30);
        Assertions.assertThat(config.review().maxAttempts()).isEqualTo(3);

        Assertions.assertThat(config.summary().failureThreshold()).isEqualTo(3);
        Assertions.assertThat(config.summary().openDurationSeconds()).isEqualTo(20);

        Assertions.assertThat(config.skill().failureThreshold()).isEqualTo(3);
        Assertions.assertThat(config.skill().openDurationSeconds()).isEqualTo(20);
    }

    @Test
    @DisplayName("backoff-max-msがbase未満でもbase値へ補正する")
    void normalizesBackoffRange() {
        var settings = new ResilienceConfig.OperationSettings(2, 10, 2, 500, 100);

        Assertions.assertThat(settings.backoffBaseMs()).isEqualTo(500);
        Assertions.assertThat(settings.backoffMaxMs()).isEqualTo(500);
    }

    @Test
    @DisplayName("summaryの部分設定時に未設定項目はsummaryデフォルトを使う")
    void summaryPartialConfigUsesSummaryDefaults() {
        var partialSummary = new ResilienceConfig.OperationSettings(0, 15, 0, 0, 0);
        var config = new ResilienceConfig(null, partialSummary, null);

        Assertions.assertThat(config.summary().failureThreshold()).isEqualTo(3);
        Assertions.assertThat(config.summary().openDurationSeconds()).isEqualTo(15);
        Assertions.assertThat(config.summary().maxAttempts()).isEqualTo(3);
        Assertions.assertThat(config.summary().backoffBaseMs()).isEqualTo(500);
        Assertions.assertThat(config.summary().backoffMaxMs()).isEqualTo(4000);
    }
}
