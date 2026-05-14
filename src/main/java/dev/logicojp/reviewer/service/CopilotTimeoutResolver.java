package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.CopilotConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Resolves Copilot-related timeout settings from centralized Micronaut configuration.
///
/// The SDK-status and SDK-auth-status timeouts are bound to the legacy
/// `cli-healthcheck-seconds` / `cli-authcheck-seconds` configuration keys for
/// backward compatibility with existing deployments. They now govern the SDK
/// `getStatus()` / `getAuthStatus()` JSON-RPC timeouts rather than CLI
/// subprocess execution.
@Singleton
public class CopilotTimeoutResolver {

    private static final Logger logger = LoggerFactory.getLogger(CopilotTimeoutResolver.class);
    private final CopilotConfig copilotConfig;

    public CopilotTimeoutResolver() {
        this(new CopilotConfig(null, null, 60, 10, 15));
    }

    @Inject
    public CopilotTimeoutResolver(CopilotConfig copilotConfig) {
        this.copilotConfig = copilotConfig;
    }

    public long resolveStartTimeoutSeconds() {
        long value = copilotConfig.startTimeoutSeconds();
        logger.debug("Resolved Copilot start timeout: {}s", value);
        return value;
    }

    /// Timeout for {@link com.github.copilot.sdk.CopilotClient#getStatus()} requests.
    /// Bound to the legacy `cli-healthcheck-seconds` configuration key.
    public long resolveSdkStatusTimeoutSeconds() {
        long value = copilotConfig.cliHealthcheckSeconds();
        logger.debug("Resolved Copilot SDK status timeout: {}s", value);
        return value;
    }

    /// Timeout for {@link com.github.copilot.sdk.CopilotClient#getAuthStatus()} requests.
    /// Bound to the legacy `cli-authcheck-seconds` configuration key.
    public long resolveSdkAuthStatusTimeoutSeconds() {
        long value = copilotConfig.cliAuthcheckSeconds();
        logger.debug("Resolved Copilot SDK auth-status timeout: {}s", value);
        return value;
    }
}
