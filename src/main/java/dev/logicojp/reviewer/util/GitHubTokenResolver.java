package dev.logicojp.reviewer.util;

import dev.logicojp.reviewer.config.CopilotConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/// Resolves a GitHub token from CLI options, environment, or gh auth.
@Singleton
public final class GitHubTokenResolver {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTokenResolver.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private final boolean ghAuthFallbackEnabled;
    private final TokenInputReader tokenInputReader;
    private final GhAuthTokenProvider ghAuthTokenProvider;

    GitHubTokenResolver(long timeoutSeconds) {
        this(timeoutSeconds, null, null, false);
    }

    GitHubTokenResolver(long timeoutSeconds,
                        @Nullable String configuredGhCliPath,
                        @Nullable String configuredPath,
                        boolean ghAuthFallbackEnabled) {
        this(
            ghAuthFallbackEnabled,
            new TokenInputReader(),
            new GhAuthTokenProvider(
                normalizeTimeout(timeoutSeconds),
                new GhCliLocator(configuredGhCliPath, configuredPath)
            )
        );
    }

    GitHubTokenResolver(boolean ghAuthFallbackEnabled,
                        TokenInputReader tokenInputReader,
                        GhAuthTokenProvider ghAuthTokenProvider) {
        this.ghAuthFallbackEnabled = ghAuthFallbackEnabled;
        this.tokenInputReader = tokenInputReader;
        this.ghAuthTokenProvider = ghAuthTokenProvider;
    }

    @Inject
    public GitHubTokenResolver(ExecutionConfig executionConfig,
                               CopilotConfig copilotConfig) {
        this(
            executionConfig.ghAuthTimeoutSeconds(),
            copilotConfig.ghCliPath(),
            CliPathResolver.systemPathValue(),
            executionConfig.isGhAuthFallbackEnabled()
        );
    }

    public GitHubTokenResolver(ExecutionConfig executionConfig) {
        this(
            executionConfig.ghAuthTimeoutSeconds(),
            null,
            CliPathResolver.systemPathValue(),
            executionConfig.isGhAuthFallbackEnabled()
        );
    }

    public Optional<String> resolve(@Nullable String providedToken) {
        String normalized = tokenInputReader.normalize(providedToken);
        if (normalized != null) {
            return Optional.of(normalized);
        }

        if (!ghAuthFallbackEnabled) {
            logger.debug("gh auth token fallback is disabled by configuration");
            return Optional.empty();
        }
        return ghAuthTokenProvider.resolve();
    }

    private static long normalizeTimeout(long timeoutSeconds) {
        return timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }
}
