package dev.logicojp.reviewer.infrastructure.logging;

import dev.logicojp.reviewer.application.port.outbound.SetLogLevelPort;
import jakarta.inject.Singleton;

/// Outbound logging adapter that keeps Logback APIs outside presentation and application.
@Singleton
public final class LogbackLoggingAdapter implements SetLogLevelPort {

    @Override
    public boolean enableVerboseLogging() {
        return LogbackLevelSwitcher.setDebug();
    }
}
