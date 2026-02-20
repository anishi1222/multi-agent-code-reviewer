package dev.logicojp.reviewer.service;

/// Exception thrown when the Copilot CLI is unavailable, unauthenticated,
/// or fails startup/health checks.
public class CopilotCliException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public CopilotCliException(String message) {
        super(message);
    }

    public CopilotCliException(String message, Throwable cause) {
        super(message, cause);
    }
}