package dev.logicojp.reviewer.application.port.outbound;

/// Outbound port: manage the lifecycle and health of the Copilot CLI client.
///
/// Implementer: {@code infrastructure.copilot.CopilotService}
/// Callers:     {@code application.review.ReviewOrchestrator}
///
/// Covers behaviors: AUTH-01–AUTH-08, AUTH-10–AUTH-11
public interface ManageCopilotClientPort {

    /// Initialize and start the Copilot client with the given token.
    ///
    /// @param token the GitHub authentication token
    /// @throws dev.logicojp.reviewer.domain.resilience.CopilotCliException if the client cannot start
    void start(String token);

    /// Stop the Copilot client and release any held resources.
    void stop();

    /// Check whether the Copilot client is currently healthy.
    ///
    /// @return {@code true} if the client is ready to accept session requests
    boolean isHealthy();
}
