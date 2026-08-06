package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver;
import jakarta.inject.Singleton;

/// Formats user-facing error messages for Copilot startup failures.
@Singleton
public class CopilotStartupErrorFormatter {

    private static final String CLI_INSTALL_AUTH_GUIDANCE =
        "Ensure GitHub Copilot CLI is installed and authenticated";
    private static final String CLIENT_TIMEOUT_ENV_GUIDANCE =
        "or set COPILOT_START_TIMEOUT_SECONDS to a higher value.";
    private static final String PROTOCOL_AUTH_GUIDANCE =
        "and authenticated (for example, run `github-copilot auth login`)";
    private static final String DOCTOR_SUGGESTION =
        " Run `review doctor` for detailed runtime diagnostics.";

    public String buildClientTimeoutMessage(long timeoutSeconds) {        return "Copilot client start timed out after " + timeoutSeconds + "s. "
            + CLI_INSTALL_AUTH_GUIDANCE + ", "
            + CLIENT_TIMEOUT_ENV_GUIDANCE
            + DOCTOR_SUGGESTION;
    }

    public String buildProtocolTimeoutMessage() {
        return "Copilot CLI ping timed out. "
            + CLI_INSTALL_AUTH_GUIDANCE + " "
            + PROTOCOL_AUTH_GUIDANCE + ", "
            + "or set " + CopilotCliPathResolver.CLI_PATH_ENV + " to the correct executable."
            + DOCTOR_SUGGESTION;
    }

    public String buildCliNotFoundMessage(String detail) {
        return "Copilot CLI not found"
            + (detail != null && !detail.isBlank() ? ": " + detail : "")
            + ". Install GitHub Copilot CLI (see `gh extension install github/gh-copilot`) "
            + "or set " + CopilotCliPathResolver.CLI_PATH_ENV + "."
            + DOCTOR_SUGGESTION;
    }

    public String buildClientNotInitializedMessage() {
        return "Copilot client is not initialized. "
            + CLI_INSTALL_AUTH_GUIDANCE + " and call `start()` first."
            + DOCTOR_SUGGESTION;
    }
}
