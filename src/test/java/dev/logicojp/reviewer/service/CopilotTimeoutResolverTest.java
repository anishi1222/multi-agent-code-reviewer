package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotTimeoutResolver")
class CopilotTimeoutResolverTest {

    @Test
    @DisplayName("Copilot起動タイムアウトを設定値から解決する")
    void resolvesStartTimeout() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, 42, 10, 15));
        long value = resolver.resolveStartTimeoutSeconds();

        assertThat(value).isEqualTo(42L);
    }

    @Test
    @DisplayName("CLIヘルスチェックタイムアウトを設定値から解決する")
    void resolvesCliHealthcheckTimeout() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, 60, 11, 15));
        long value = resolver.resolveCliHealthcheckSeconds();

        assertThat(value).isEqualTo(11L);
    }

    @Test
    @DisplayName("CLI認証チェックタイムアウトを設定値から解決する")
    void resolvesCliAuthcheckTimeout() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, 60, 10, 17));
        long value = resolver.resolveCliAuthcheckSeconds();

        assertThat(value).isEqualTo(17L);
    }

    @Test
    @DisplayName("不正値はCopilotConfig側で正規化される")
    void normalizesInvalidValuesViaConfig() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, -1, 0, -5));

        assertThat(resolver.resolveStartTimeoutSeconds()).isEqualTo(60L);
        assertThat(resolver.resolveCliHealthcheckSeconds()).isEqualTo(10L);
        assertThat(resolver.resolveCliAuthcheckSeconds()).isEqualTo(15L);
    }
}
