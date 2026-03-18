package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.CopilotConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Resolves Copilot-related timeout settings from centralized Micronaut configuration.
@Singleton
public class CopilotTimeoutResolver {

    private static final Logger logger = LoggerFactory.getLogger(CopilotTimeoutResolver.class);
    private final CopilotConfig copilotConfig;

    public CopilotTimeoutResolver() {
        this(new CopilotConfig(null, null, null, 60, 10, 15));
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

    public long resolveCliHealthcheckSeconds() {
        long value = copilotConfig.cliHealthcheckSeconds();
        logger.debug("Resolved Copilot CLI healthcheck timeout: {}s", value);
        return value;
    }

    public long resolveCliAuthcheckSeconds() {
        long value = copilotConfig.cliAuthcheckSeconds();
        logger.debug("Resolved Copilot CLI authcheck timeout: {}s", value);
        return value;
    }
}
