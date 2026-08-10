package dev.logicojp.reviewer.application.port.outbound;

/// Outbound port for changing the concrete logging backend at runtime.
@FunctionalInterface
public interface SetLogLevelPort {

    /// Enables verbose logging.
    ///
    /// @return {@code true} when the active backend accepted the change
    boolean enableVerboseLogging();
}
