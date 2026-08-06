package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Constructs a {@link ReviewRequest} (application DTO) from presentation-layer inputs.
///
/// Does not import any infrastructure types — rubber duck defaults are merged
/// via per-agent config overrides in {@link ReviewAgentConfigResolver}.
@Singleton
public class ReviewRunRequestFactory {

    /// Creates a {@link ReviewRequest} from the resolved presentation-layer values.
    ///
    /// @param options            parsed CLI options
    /// @param target             resolved review target
    /// @param agentConfigs       resolved and rubber-duck-adjusted agent configs
    /// @param outputDirectory    resolved output directory
    /// @param invocationTimestamp timestamp set at CLI startup for session correlation
    /// @param resolvedToken      resolved GitHub token (empty string for local review)
    /// @param resolvedModels     resolved model names from config/CLI
    public ReviewRequest create(
            ReviewOptions options,
            ReviewTarget target,
            Map<String, AgentConfig> agentConfigs,
            Path outputDirectory,
            String invocationTimestamp,
            String resolvedToken,
            ReviewModelConfigResolver.ResolvedModels resolvedModels) {

        return new ReviewRequest(
            target,
            List.copyOf(agentConfigs.values()),
            options.parallelism(),
            outputDirectory,
            List.of(),               // focusAreas: not yet surfaced in CLI
            null,                    // localFileConfig: resolved by infrastructure adapter
            options.rubberDuck(),
            resolvedToken != null ? resolvedToken : "",
            invocationTimestamp,
            resolvedModels.reasoningEffort(),
            options.noSharedSession(),
            options.noSummary()
        );
    }
}
