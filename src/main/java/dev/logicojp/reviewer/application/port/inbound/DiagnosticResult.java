package dev.logicojp.reviewer.application.port.inbound;

import java.util.Objects;

/// Result of a single diagnostic check.
///
/// @param checkName  human-readable name of the check (e.g. "copilot-cli-auth")
/// @param passed     whether the check passed
/// @param message    description of the result (or error if not passed)
/// @param detail     optional additional detail (empty string if none)
public record DiagnosticResult(
    String checkName,
    boolean passed,
    String message,
    String detail
) {

    public DiagnosticResult {
        Objects.requireNonNull(checkName, "checkName must not be null");
        message = message != null ? message : "";
        detail = detail != null ? detail : "";
    }

    /// Creates a passing diagnostic result.
    public static DiagnosticResult pass(String checkName, String message) {
        return new DiagnosticResult(checkName, true, message, "");
    }

    /// Creates a failing diagnostic result.
    public static DiagnosticResult fail(String checkName, String message) {
        return new DiagnosticResult(checkName, false, message, "");
    }

    /// Creates a failing diagnostic result with additional detail.
    public static DiagnosticResult fail(String checkName, String message, String detail) {
        return new DiagnosticResult(checkName, false, message, detail);
    }
}
