package dev.logicojp.reviewer;

import org.assertj.core.api.Assertions;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@DisplayName("LogbackLevelSwitcher")
class LogbackLevelSwitcherTest {

    @Test
    @DisplayName("setDebugがtrueを返し、ログレベルがDEBUGに設定される")
    void setDebugReturnsTrueAndSetsLevel() {
        boolean result = LogbackLevelSwitcher.setDebug();

        Assertions.assertThat(result).isTrue();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Assertions.assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel())
                .isEqualTo(Level.DEBUG);
        Assertions.assertThat(context.getLogger("dev.logicojp").getLevel())
                .isEqualTo(Level.DEBUG);
        Assertions.assertThat(context.getLogger("com.github.copilot.sdk").getLevel())
                .isEqualTo(Level.WARN);
    }
}
