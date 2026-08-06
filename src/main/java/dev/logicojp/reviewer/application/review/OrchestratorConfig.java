package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.domain.review.PromptTexts;

import java.util.Objects;

/// Application-layer configuration for the review orchestrator.
///
/// Purified from {@code orchestrator.OrchestratorConfig}:
/// - Removed all {@code @Nullable} annotations (Micronaut).
/// - Removed {@code TemplateService} (replaced by {@code LoadTemplatePort} injected into
///   {@code ReviewOrchestrator} directly).
/// - Removed SDK-coupled types ({@code GithubMcpConfig}, {@code LocalFileConfig},
///   {@code ExecutionConfig}, {@code RubberDuckConfig}) — execution parameters
///   are now plain values or derived from {@code ReviewRequest} at call time.
import dev.logicojp.reviewer.shared.PromptBudget;

public record OrchestratorConfig(
    String githubToken,
    long orchestratorTimeoutMinutes,
    long agentTimeoutMinutes,
    int reviewPasses,
    int maxRetries,
    boolean sharedSessionEnabled,
    String reasoningEffort,
    String outputConstraints,
    String invocationTimestamp,
    PromptTexts promptTexts,
    boolean rubberDuckEnabled,
    int rubberDuckRounds,
    PromptBudget promptBudget
) {

    public static final long DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES = 10L;
    public static final long DEFAULT_AGENT_TIMEOUT_MINUTES = 5L;
    public static final int DEFAULT_REVIEW_PASSES = 1;
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final int DEFAULT_RUBBER_DUCK_ROUNDS = 2;

    public OrchestratorConfig {
        orchestratorTimeoutMinutes = orchestratorTimeoutMinutes > 0
            ? orchestratorTimeoutMinutes : DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES;
        agentTimeoutMinutes = agentTimeoutMinutes > 0
            ? agentTimeoutMinutes : DEFAULT_AGENT_TIMEOUT_MINUTES;
        reviewPasses = reviewPasses > 0 ? reviewPasses : DEFAULT_REVIEW_PASSES;
        maxRetries = maxRetries >= 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        rubberDuckRounds = rubberDuckRounds > 0 ? rubberDuckRounds : DEFAULT_RUBBER_DUCK_ROUNDS;
        invocationTimestamp = invocationTimestamp != null ? invocationTimestamp : "unknown-start-time";
        promptTexts = promptTexts != null ? promptTexts : new PromptTexts(null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String githubToken;
        private long orchestratorTimeoutMinutes = DEFAULT_ORCHESTRATOR_TIMEOUT_MINUTES;
        private long agentTimeoutMinutes = DEFAULT_AGENT_TIMEOUT_MINUTES;
        private int reviewPasses = DEFAULT_REVIEW_PASSES;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private boolean sharedSessionEnabled = true;
        private PromptBudget promptBudget = new PromptBudget();
        private String reasoningEffort;
        private String outputConstraints;
        private String invocationTimestamp;
        private PromptTexts promptTexts;
        private boolean rubberDuckEnabled;
        private int rubberDuckRounds = DEFAULT_RUBBER_DUCK_ROUNDS;

        public Builder githubToken(String githubToken) { this.githubToken = githubToken; return this; }
        public Builder orchestratorTimeoutMinutes(long v) { this.orchestratorTimeoutMinutes = v; return this; }
        public Builder agentTimeoutMinutes(long v) { this.agentTimeoutMinutes = v; return this; }
        public Builder reviewPasses(int v) { this.reviewPasses = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder sharedSessionEnabled(boolean v) { this.sharedSessionEnabled = v; return this; }
        public Builder reasoningEffort(String v) { this.reasoningEffort = v; return this; }
        public Builder outputConstraints(String v) { this.outputConstraints = v; return this; }
        public Builder invocationTimestamp(String v) { this.invocationTimestamp = v; return this; }
        public Builder promptTexts(PromptTexts v) { this.promptTexts = v; return this; }
        public Builder rubberDuckEnabled(boolean v) { this.rubberDuckEnabled = v; return this; }
        public Builder rubberDuckRounds(int v) { this.rubberDuckRounds = v; return this; }
        public Builder promptBudget(PromptBudget v) { this.promptBudget = v; return this; }

        public OrchestratorConfig build() {
            return new OrchestratorConfig(githubToken, orchestratorTimeoutMinutes, agentTimeoutMinutes,
                reviewPasses, maxRetries, sharedSessionEnabled, reasoningEffort, outputConstraints,
                invocationTimestamp, promptTexts, rubberDuckEnabled, rubberDuckRounds, promptBudget);
        }
    }

    @Override
    public String toString() {
        return "OrchestratorConfig{timeout=%d, agentTimeout=%d, passes=%d, retries=%d, rubberDuck=%b}"
            .formatted(orchestratorTimeoutMinutes, agentTimeoutMinutes, reviewPasses, maxRetries, rubberDuckEnabled);
    }
}
