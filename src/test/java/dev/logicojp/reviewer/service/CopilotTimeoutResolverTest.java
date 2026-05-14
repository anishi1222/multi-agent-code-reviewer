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
    @DisplayName("SDKステータスタイムアウトを設定値から解決する")
    void resolvesSdkStatusTimeout() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, 60, 11, 15));
        long value = resolver.resolveSdkStatusTimeoutSeconds();

        assertThat(value).isEqualTo(11L);
    }

    @Test
    @DisplayName("SDK認証ステータスタイムアウトを設定値から解決する")
    void resolvesSdkAuthStatusTimeout() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, 60, 10, 17));
        long value = resolver.resolveSdkAuthStatusTimeoutSeconds();

        assertThat(value).isEqualTo(17L);
    }

    @Test
    @DisplayName("不正値はCopilotConfig側で正規化される")
    void normalizesInvalidValuesViaConfig() {
        var resolver = new CopilotTimeoutResolver(new CopilotConfig(null, null, -1, 0, -5));

        assertThat(resolver.resolveStartTimeoutSeconds()).isEqualTo(60L);
        assertThat(resolver.resolveSdkStatusTimeoutSeconds()).isEqualTo(10L);
        assertThat(resolver.resolveSdkAuthStatusTimeoutSeconds()).isEqualTo(15L);
    }
}
