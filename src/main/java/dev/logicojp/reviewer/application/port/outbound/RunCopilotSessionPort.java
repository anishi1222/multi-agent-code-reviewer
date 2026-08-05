package dev.logicojp.reviewer.application.port.outbound;

/// Outbound port: create and run a single Copilot review session.
///
/// Implementer: {@code infrastructure.copilot.ReviewSessionExecutor}
/// Callers:     {@code application.review.ReviewPassRunner}
///
/// Covers behaviors: ORC-02, ORC-03, ORC-06
public interface RunCopilotSessionPort {

    /// Create and run a Copilot review session, returning the response.
    ///
    /// The implementation is responsible for mapping {@link SessionRequest} to
    /// the SDK {@code SessionConfig} and handling SDK-level exceptions.
    ///
    /// @param request the session parameters (agent config, prompt, MCP servers)
    /// @return the session response text
    /// @throws dev.logicojp.reviewer.domain.resilience.CopilotCliException if the Copilot client is unavailable
    String runSession(SessionRequest request);
}
