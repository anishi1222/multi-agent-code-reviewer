package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.annotation.Nullable;

/// Type-safe configuration for Copilot CLI/runtime integration.
@ConfigurationProperties("reviewer.copilot")
public record CopilotConfig(
    @Nullable String cliPath,
    @Nullable String ghCliPath,
    @Nullable String githubToken,
    @Bindable(defaultValue = "60") long startTimeoutSeconds,
    @Bindable(defaultValue = "10") long cliHealthcheckSeconds,
    @Bindable(defaultValue = "15") long cliAuthcheckSeconds
) {

    private static final long DEFAULT_START_TIMEOUT_SECONDS = 60;
    private static final long DEFAULT_CLI_HEALTHCHECK_SECONDS = 10;
    private static final long DEFAULT_CLI_AUTHCHECK_SECONDS = 15;

    public CopilotConfig {
        startTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(startTimeoutSeconds, DEFAULT_START_TIMEOUT_SECONDS);
        cliHealthcheckSeconds = ConfigDefaults.defaultIfNonPositive(
            cliHealthcheckSeconds,
            DEFAULT_CLI_HEALTHCHECK_SECONDS
        );
        cliAuthcheckSeconds = ConfigDefaults.defaultIfNonPositive(
            cliAuthcheckSeconds,
            DEFAULT_CLI_AUTHCHECK_SECONDS
        );
    }
}
