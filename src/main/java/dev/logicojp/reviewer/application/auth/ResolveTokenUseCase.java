package dev.logicojp.reviewer.application.auth;

import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/// Application use-case: resolve the GitHub token to use for this invocation.
///
/// Implements {@link ResolveTokenPort}. Holds the precedence policy and the fallback switch;
/// the mechanisms for actually obtaining a token live behind {@link AcquireGitHubTokenPort}.
///
/// Precedence:
/// <ol>
///   <li>the value the caller supplied (including {@code -}, which the adapter reads from stdin)</li>
///   <li>the {@code gh} CLI's stored credentials — only when the fallback is enabled</li>
/// </ol>
///
/// Before t16.1 this policy lived in {@code infrastructure.auth.GitHubTokenResolver}, which
/// implemented the *inbound* port directly — a direction inversion recorded as ADR-0006
/// deviation #1. The token is never logged, and no resolved value is retained in a field.
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
public final class ResolveTokenUseCase implements ResolveTokenPort {

    private static final Logger logger = Logger.getLogger(ResolveTokenUseCase.class.getName());

    private final AcquireGitHubTokenPort tokenSource;
    private final boolean ghAuthFallbackEnabled;

    /// @param tokenSource           outbound port over the host's credential channels
    /// @param ghAuthFallbackEnabled whether `gh auth token` may be consulted when no token was given
    public ResolveTokenUseCase(AcquireGitHubTokenPort tokenSource, boolean ghAuthFallbackEnabled) {
        this.tokenSource = Objects.requireNonNull(tokenSource, "tokenSource must not be null");
        this.ghAuthFallbackEnabled = ghAuthFallbackEnabled;
    }

    @Override
    public Optional<String> resolve(String providedToken) {
        Optional<String> supplied = tokenSource.fromProvidedValue(providedToken);
        if (supplied.isPresent()) {
            return supplied;
        }
        if (!ghAuthFallbackEnabled) {
            logger.fine("ResolveTokenUseCase: no token supplied and gh CLI fallback is disabled");
            return Optional.empty();
        }
        return tokenSource.fromGhCli();
    }
}
