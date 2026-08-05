package dev.logicojp.reviewer.infrastructure.auth;

import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/// Adapter over the host's GitHub credential channels.
///
/// Implements the **outbound** port {@link AcquireGitHubTokenPort}: it exposes the two mechanisms
/// (normalise a caller-supplied value, ask the `gh` CLI) and nothing else. Which mechanism wins,
/// and whether the CLI may be consulted at all, is policy owned by
/// {@code application.auth.ResolveTokenUseCase}.
///
/// Until t16.1 this class implemented the *inbound* {@code ResolveTokenPort} directly and carried
/// the precedence policy — ADR-0006 deviation #1. Do not reintroduce policy here: Rule 4 of
/// {@code LayerDependencyRulesTest} now fails on any infrastructure reference to
/// {@code application.port.inbound}.
@Singleton
public final class GitHubTokenResolver implements AcquireGitHubTokenPort {

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private final TokenInputReader tokenInputReader;
    private final GhAuthTokenProvider ghAuthTokenProvider;

    GitHubTokenResolver(long timeoutSeconds) {
        this(timeoutSeconds, null, null);
    }

    GitHubTokenResolver(long timeoutSeconds,
                        @Nullable String configuredGhCliPath,
                        @Nullable String configuredPath) {
        this(
            new TokenInputReader(),
            new GhAuthTokenProvider(
                normalizeTimeout(timeoutSeconds),
                new GhCliLocator(configuredGhCliPath, configuredPath)
            )
        );
    }

    GitHubTokenResolver(TokenInputReader tokenInputReader,
                        GhAuthTokenProvider ghAuthTokenProvider) {
        this.tokenInputReader = tokenInputReader;
        this.ghAuthTokenProvider = ghAuthTokenProvider;
    }

    @Inject
    public GitHubTokenResolver(ExecutionConfig executionConfig, CopilotConfig copilotConfig) {
        this(
            executionConfig.ghAuthTimeoutSeconds(),
            copilotConfig.ghCliPath(),
            CliPathResolver.systemPathValue()
        );
    }

    @Override
    public Optional<String> fromProvidedValue(@Nullable String providedToken) {
        return Optional.ofNullable(tokenInputReader.normalize(providedToken));
    }

    @Override
    public Optional<String> fromGhCli() {
        return ghAuthTokenProvider.resolve();
    }

    private static long normalizeTimeout(long timeoutSeconds) {
        return timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }
}
