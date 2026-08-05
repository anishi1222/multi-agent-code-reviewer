package dev.logicojp.reviewer.infrastructure.auth;

/// Exception thrown when the Copilot CLI is not found, not authenticated,
/// or fails health checks.
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
