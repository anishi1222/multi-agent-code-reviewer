package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for [CopilotConfig] timeout normalisation.
///
/// Ported from the pre-migration `service.CopilotTimeoutResolverTest` (T013). The legacy
/// `CopilotTimeoutResolver` was a pass-through over this record, and its normalisation of
/// non-positive values now lives in the record's compact constructor — so the behaviour it
/// guarded is asserted here instead of being lost with the deleted class.
@DisplayName("CopilotConfig")
class CopilotConfigTest {

    @Nested
    @DisplayName("configured timeouts")
    class ConfiguredTimeouts {

        @Test
        @DisplayName("start timeoutは設定値をそのまま公開する")
        void resolvesStartTimeout() {
            var config = new CopilotConfig(null, null, 42, 10, 15);

            assertThat(config.startTimeoutSeconds()).isEqualTo(42L);
        }

        @Test
        @DisplayName("CLI healthcheck timeoutは設定値をそのまま公開する")
        void resolvesSdkStatusTimeout() {
            var config = new CopilotConfig(null, null, 60, 11, 15);

            assertThat(config.cliHealthcheckSeconds()).isEqualTo(11L);
        }

        @Test
        @DisplayName("CLI authcheck timeoutは設定値をそのまま公開する")
        void resolvesSdkAuthStatusTimeout() {
            var config = new CopilotConfig(null, null, 60, 10, 17);

            assertThat(config.cliAuthcheckSeconds()).isEqualTo(17L);
        }
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("非正の値は既定値に正規化される")
        void normalizesInvalidValuesViaConfig() {
            var config = new CopilotConfig(null, null, -1, 0, -5);

            assertThat(config.startTimeoutSeconds()).isEqualTo(60L);
            assertThat(config.cliHealthcheckSeconds()).isEqualTo(10L);
            assertThat(config.cliAuthcheckSeconds()).isEqualTo(15L);
        }

        @ParameterizedTest(name = "{0} は既定値に置き換えられる")
        @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
        @DisplayName("ゼロ・負値・下限値はいずれも既定値になる")
        void nonPositiveValuesFallBackToDefaults(long invalid) {
            var config = new CopilotConfig(null, null, invalid, invalid, invalid);

            assertThat(config.startTimeoutSeconds()).isEqualTo(60L);
            assertThat(config.cliHealthcheckSeconds()).isEqualTo(10L);
            assertThat(config.cliAuthcheckSeconds()).isEqualTo(15L);
        }

        @Test
        @DisplayName("CLIパスはnullを許容する")
        void allowsNullCliPaths() {
            var config = new CopilotConfig(null, null, 1, 1, 1);

            assertThat(config.cliPath()).isNull();
            assertThat(config.ghCliPath()).isNull();
        }
    }
}
