package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Configuration for Copilot CLI path and startup/health-check timeouts.
/// Bound to `reviewer.copilot` in YAML.
@ConfigurationProperties("reviewer.copilot")
public record CopilotConfig(
    String cliPath,
    String githubToken,
    long healthcheckSeconds,
    long authcheckSeconds,
    long startTimeoutSeconds
) {

    public static final long DEFAULT_HEALTHCHECK_SECONDS = 10;
    public static final long DEFAULT_AUTHCHECK_SECONDS = 15;
    public static final long DEFAULT_START_TIMEOUT_SECONDS = 60;

    public CopilotConfig {
        cliPath = cliPath != null ? cliPath.trim() : "";
        githubToken = githubToken != null ? githubToken.trim() : "";
        healthcheckSeconds = ConfigDefaults.defaultIfNonPositive(
            healthcheckSeconds, DEFAULT_HEALTHCHECK_SECONDS);
        authcheckSeconds = ConfigDefaults.defaultIfNonPositive(
            authcheckSeconds, DEFAULT_AUTHCHECK_SECONDS);
        startTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(
            startTimeoutSeconds, DEFAULT_START_TIMEOUT_SECONDS);
    }

    @Override
    public String toString() {
        return "CopilotConfig[cliPath=%s, githubToken=%s, healthcheckSeconds=%d, authcheckSeconds=%d, startTimeoutSeconds=%d]"
            .formatted(cliPath,
                       githubToken != null && !githubToken.isBlank() ? "***" : "(empty)",
                       healthcheckSeconds, authcheckSeconds, startTimeoutSeconds);
    }
}