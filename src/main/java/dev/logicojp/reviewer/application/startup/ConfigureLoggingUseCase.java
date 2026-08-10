package dev.logicojp.reviewer.application.startup;

import dev.logicojp.reviewer.application.port.inbound.ConfigureLoggingPort;
import dev.logicojp.reviewer.application.port.outbound.SetLogLevelPort;

import java.util.Objects;

/// Application use case that exposes logging configuration to the CLI without leaking its backend.
public final class ConfigureLoggingUseCase implements ConfigureLoggingPort {

    private final SetLogLevelPort logLevel;

    public ConfigureLoggingUseCase(SetLogLevelPort logLevel) {
        this.logLevel = Objects.requireNonNull(logLevel, "logLevel must not be null");
    }

    @Override
    public boolean enableVerboseLogging() {
        return logLevel.enableVerboseLogging();
    }
}
