package dev.logicojp.reviewer.application.port.outbound;

import java.util.Optional;

/// Outbound port: obtain a GitHub token from one of the host's credential channels.
///
/// Implementer: {@code infrastructure.auth.GitHubTokenResolver}
/// Callers:     {@code application.auth.ResolveTokenUseCase}
///
/// This port exposes the two *mechanisms* only. Which one wins, and whether the CLI fallback may
/// be consulted at all, is policy and belongs to the use case — see
/// {@code application.auth.ResolveTokenUseCase}.
public interface AcquireGitHubTokenPort {

    /// Normalises a caller-supplied raw token.
    ///
    /// Trims surrounding whitespace, treats blank input as absent, and reads standard input when
    /// the value is the `-` marker.
    ///
    /// @param providedToken raw token string ({@code null}, a literal value, or {@code "-"})
    /// @return the normalised token, or empty when the caller supplied none
    Optional<String> fromProvidedValue(String providedToken);

    /// Obtains a token from the `gh` CLI's stored authentication state.
    ///
    /// @return the token reported by the CLI, or empty when it is unavailable or not authenticated
    Optional<String> fromGhCli();
}
