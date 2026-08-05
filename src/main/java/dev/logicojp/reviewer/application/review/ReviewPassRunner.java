package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.AgentPromptBuilder;
import dev.logicojp.reviewer.domain.agent.ReviewTargetInstructionResolver;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/// Executes a series of review passes for a single agent against a review target.
///
/// NEW class replacing the old {@code AgentReviewer} + {@code ReviewAgent.review()} pattern.
/// Uses {@link RunCopilotSessionPort} (dependency injection) instead of the old SDK
/// {@code CopilotClient} directly.
///
/// Collaborators (constructor-injected, no DI annotations):
/// - {@link RunCopilotSessionPort} — runs each Copilot session
/// - {@link ReviewRetryExecutor} — applies retry/circuit-breaker logic per pass
/// - {@link ReviewResultFactory} — constructs domain result objects from raw content
public final class ReviewPassRunner {

    private static final Logger logger = Logger.getLogger(ReviewPassRunner.class.getName());

    private final RunCopilotSessionPort copilotSession;
    private final ReviewResultFactory reviewResultFactory;

    public ReviewPassRunner(RunCopilotSessionPort copilotSession,
                            ReviewResultFactory reviewResultFactory) {
        this.copilotSession = copilotSession;
        this.reviewResultFactory = reviewResultFactory;
    }

    /// Executes the requested number of review passes for the given agent and target.
    ///
    /// Each pass runs independently with retry logic applied per-pass.
    ///
    /// @param config      agent configuration
    /// @param target      review target
    /// @param context     shared review context (output constraints, reasoning effort, etc.)
    /// @param passes      number of review passes to run
    /// @param mcpServers  MCP server specifications for this agent
    /// @param maxRetries  maximum retries per pass on transient failures
    /// @return list of review results (one per pass); failures are included as error results
    public List<ReviewResult> run(AgentConfig config,
                                  ReviewTarget target,
                                  ReviewContext context,
                                  int passes,
                                  List<McpServerSpec> mcpServers,
                                  int maxRetries) {
        var results = new ArrayList<ReviewResult>(passes);
        var resolver = new ReviewTargetInstructionResolver(config);
        var resolved = resolver.resolve(target, context.cachedSourceContent());
        boolean usesMcp = !mcpServers.isEmpty();

        for (int pass = 1; pass <= passes; pass++) {
            int currentPass = pass;
            var retryExecutor = new ReviewRetryExecutor(config.name() + "#" + pass, maxRetries);
            ReviewResult result = retryExecutor.execute(
                () -> executePass(config, target, resolved, mcpServers, context, currentPass, usesMcp),
                e -> reviewResultFactory.fromException(config, target.displayName(), e)
            );
            results.add(result);
        }
        return List.copyOf(results);
    }

    private ReviewResult executePass(AgentConfig config,
                                     ReviewTarget target,
                                     ReviewTargetInstructionResolver.ResolvedInstruction resolved,
                                     List<McpServerSpec> mcpServers,
                                     ReviewContext context,
                                     int passNumber,
                                     boolean usesMcp) {
        logger.info(() -> "Agent '" + config.name() + "' starting pass " + passNumber
            + " on '" + target.displayName() + "'");

        String prompt = buildPrompt(resolved, config, context);
        Map<String, String> params = buildSessionParams(context);
        var sessionRequest = new SessionRequest(config, prompt, mcpServers, params);
        String content = copilotSession.runSession(sessionRequest);
        return reviewResultFactory.fromContent(config, target.displayName(), content, usesMcp);
    }

    private String buildPrompt(ReviewTargetInstructionResolver.ResolvedInstruction resolved,
                               AgentConfig config,
                               ReviewContext context) {
        String base = resolved.instruction();
        if (resolved.isLocal() && resolved.localSourceContent() != null) {
            return AgentPromptBuilder.buildLocalInstruction(config, "", resolved.localSourceContent());
        }
        if (context.outputConstraints() != null && !context.outputConstraints().isBlank()) {
            return base + "\n\n" + context.outputConstraints();
        }
        return base;
    }

    private Map<String, String> buildSessionParams(ReviewContext context) {
        if (context.reasoningEffort() != null && !context.reasoningEffort().isBlank()) {
            return Map.of("reasoningEffort", context.reasoningEffort());
        }
        return Map.of();
    }
}
