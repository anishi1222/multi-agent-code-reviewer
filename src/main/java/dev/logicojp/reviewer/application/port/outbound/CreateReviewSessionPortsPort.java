package dev.logicojp.reviewer.application.port.outbound;

import java.util.Objects;

/// Outbound factory port for SDK-backed, invocation-scoped review session adapters.
public interface CreateReviewSessionPortsPort {

    ReviewSessionPorts create(ReviewSessionOptions options);

    record ReviewSessionOptions(long agentTimeoutMinutes, String invocationTimestamp) {
        public ReviewSessionOptions {
            invocationTimestamp = Objects.requireNonNullElse(invocationTimestamp, "unknown");
        }
    }

    record ReviewSessionPorts(
        RunCopilotSessionPort runCopilotSession,
        RunRubberDuckSessionPort runRubberDuckSession
    ) {
        public ReviewSessionPorts {
            Objects.requireNonNull(runCopilotSession, "runCopilotSession must not be null");
            Objects.requireNonNull(runRubberDuckSession, "runRubberDuckSession must not be null");
        }
    }
}
