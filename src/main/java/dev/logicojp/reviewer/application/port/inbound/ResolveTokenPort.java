package dev.logicojp.reviewer.application.port.inbound;

import java.util.Optional;

/// Inbound port: resolve a GitHub personal access token from a raw input string.
///
/// Implementer: {@code infrastructure.auth.GitHubTokenResolver}
/// Callers:     {@code presentation.ReviewTargetResolver},
///              {@code presentation.SkillExecutionPreparation}
///
/// Token sources (in order):
/// <ol>
///   <li>Explicit value provided by the caller</li>
///   <li>{@code -} (stdin marker) — reads token from standard input</li>
///   <li>gh CLI fallback (if enabled by configuration)</li>
/// </ol>
public interface ResolveTokenPort {

    /// Resolves a GitHub token from the given raw input string.
    ///
    /// @param providedToken raw token string ({@code null}, literal value, or {@code "-"} for stdin)
    /// @return resolved token value, or empty if no token could be obtained
    Optional<String> resolve(String providedToken);
}
