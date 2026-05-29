package dev.logicojp.reviewer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogbackLevelSwitcher")
class LogbackLevelSwitcherTest {

    private final LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Level originalRoot = ctx.getLogger(Logger.ROOT_LOGGER_NAME).getLevel();
    private final Level originalApp = ctx.getLogger("dev.logicojp").getLevel();
    private final Level originalCopilotSdk = ctx.getLogger("com.github.copilot").getLevel();

    @AfterEach
    void restoreLogLevels() {
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(originalRoot);
        ctx.getLogger("dev.logicojp").setLevel(originalApp);
        ctx.getLogger("com.github.copilot").setLevel(originalCopilotSdk);
    }

    @Test
    @DisplayName("setDebugはtrueを返しルートロガーをDEBUGに設定する")
    void setDebugReturnsTrueAndSetsRootToDebug() {
        boolean result = LogbackLevelSwitcher.setDebug();

        assertThat(result).isTrue();
        assertThat(ctx.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    @DisplayName("setDebugはアプリケーションロガーもDEBUGに設定する")
    void setDebugSetsAppLoggerToDebug() {
        LogbackLevelSwitcher.setDebug();

        assertThat(ctx.getLogger("dev.logicojp").getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    @DisplayName("setDebugでもCopilot SDKロガーはWARNを維持する")
    void setDebugKeepsCopilotSdkLoggerAtWarn() {
        LogbackLevelSwitcher.setDebug();

        assertThat(ctx.getLogger("com.github.copilot").getLevel()).isEqualTo(Level.WARN);
    }
}
