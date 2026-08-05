package dev.logicojp.reviewer.domain.resilience;

/// Exception thrown when the Copilot CLI is not found, not authenticated,
/// or fails health checks.
///
/// Provides clear semantics for CLI-related failures without depending on
/// any infrastructure or framework type.
public final class CopilotCliException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public CopilotCliException(String message) {
        super(message);
    }

    public CopilotCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
