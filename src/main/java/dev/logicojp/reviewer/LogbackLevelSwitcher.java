package dev.logicojp.reviewer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Isolates Logback-specific log-level switching from the SLF4J abstraction.
/// SLF4J does not provide a dynamic log-level API, so direct Logback access
/// is required for the {@code --verbose} CLI flag.
final class LogbackLevelSwitcher {

    private LogbackLevelSwitcher() {
    }

    /// Sets the root and application loggers to DEBUG level.
    /// @return true if successful, false if Logback is not the active SLF4J binding
    static boolean setDebug() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
            context.getLogger("dev.logicojp").setLevel(Level.DEBUG);
            context.getLogger("com.github.copilot").setLevel(Level.WARN);
            context.getLogger("org.yaml.snakeyaml").setLevel(Level.WARN);
            return true;
        } catch (ClassCastException _) {
            return false;
        }
    }
}
