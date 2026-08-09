package dev.logicojp.reviewer.infrastructure.copilot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Executable coverage for PM behaviour AUTH-10.
@DisplayName("deprecated Copilot token API warning contract")
class CopilotDeprecatedTokenWarningContractTest {

    private static final String LEGACY_TOKEN = "legacy-secret-token";

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger serviceLogger;

    @BeforeEach
    void attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        serviceLogger = context.getLogger(CopilotService.class);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        serviceLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("token付きstart APIは初期化成否に関係なく非推奨警告を出しtokenを記録しない")
    void logsRedactedDeprecationWarningWhenLegacyTokenApiIsInvoked(@TempDir Path tempDir) {
        CopilotConfig config = new CopilotConfig(
            tempDir.resolve("missing-copilot-cli").toString(),
            null,
            1,
            1,
            1
        );
        CopilotService service = new CopilotService(
            new CopilotCliPathResolver(config),
            new CopilotHealthProbe(config),
            config,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );

        assertThatThrownBy(() -> service.start(LEGACY_TOKEN))
            .isInstanceOf(CopilotCliException.class);

        assertThat(appender.list)
            .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
            .extracting(ILoggingEvent::getFormattedMessage)
            .anySatisfy(message -> {
                assertThat(message).containsIgnoringCase("deprecated");
                assertThat(message).doesNotContain(LEGACY_TOKEN);
            });
    }
}
