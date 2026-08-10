package dev.logicojp.reviewer.application.port.inbound;

/// Inbound port for CLI-requested runtime logging changes.
///
/// Implementer: {@code application.startup.ConfigureLoggingUseCase}
/// Caller:      {@code presentation.CliApplication}
public interface ConfigureLoggingPort {

    /// Enables verbose logging for the current process.
    ///
    /// @return {@code true} when the active logging backend accepted the change
    boolean enableVerboseLogging();
}
