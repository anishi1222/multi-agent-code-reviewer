package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryConfig")
class SummaryConfigTest {

    @Test
    @DisplayName("0以下の値はデフォルト値に正規化される")
    void invalidValuesFallbackToDefaults() {
        SummaryConfig config = new SummaryConfig(0, -1, 0, 0, 0, 0);

        assertThat(config.maxContentPerAgent()).isEqualTo(SummaryConfig.DEFAULT_MAX_CONTENT_PER_AGENT);
        assertThat(config.maxTotalPromptContent()).isEqualTo(SummaryConfig.DEFAULT_MAX_TOTAL_PROMPT_CONTENT);
        assertThat(config.fallbackExcerptLength()).isEqualTo(SummaryConfig.DEFAULT_FALLBACK_EXCERPT_LENGTH);
    }

    @Test
    @DisplayName("正の値はそのまま保持される")
    void positiveValuesArePreserved() {
        SummaryConfig config = new SummaryConfig(10_000, 50_000, 100, 0, 0, 0);

        assertThat(config.maxContentPerAgent()).isEqualTo(10_000);
        assertThat(config.maxTotalPromptContent()).isEqualTo(50_000);
        assertThat(config.fallbackExcerptLength()).isEqualTo(100);
    }
}
