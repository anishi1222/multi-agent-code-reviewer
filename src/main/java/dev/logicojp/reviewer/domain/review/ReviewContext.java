package dev.logicojp.reviewer.domain.review;

import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;

import java.util.Objects;

/// Pure domain context for executing review agents.
///
/// Replaces the old {@code agent.ReviewContext} which contained SDK types
/// ({@code CopilotClient}, {@code McpServerConfig}, etc.).
/// All infrastructure concerns have been extracted to the application / infrastructure layers.
///
/// @param invocationTimestamp  CLI invocation timestamp shared across the run
/// @param reasoningEffort      reasoning effort level for reasoning models (null = not specified)
/// @param outputConstraints    output constraints template content (null = not specified)
/// @param cachedSourceContent  pre-computed local source code for local-directory reviews (null = not a local review)
/// @param sharedSessionEnabled whether multi-pass execution can reuse a single shared session
/// @param maxRetries           number of retries on transient failures (0 = no retries)
/// @param reviewCircuitBreaker shared circuit breaker for review-domain failures
public record ReviewContext(
    String invocationTimestamp,
    String reasoningEffort,
    String outputConstraints,
    String cachedSourceContent,
    boolean sharedSessionEnabled,
    int maxRetries,
    SharedCircuitBreaker reviewCircuitBreaker
) {

    private static final SharedCircuitBreaker DEFAULT_CIRCUIT_BREAKER =
        SharedCircuitBreaker.forReviewDomain();

    public ReviewContext {
        invocationTimestamp = invocationTimestamp != null ? invocationTimestamp : "unknown-start-time";
        reviewCircuitBreaker = reviewCircuitBreaker != null ? reviewCircuitBreaker : DEFAULT_CIRCUIT_BREAKER;
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got: " + maxRetries);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String invocationTimestamp;
        private String reasoningEffort;
        private String outputConstraints;
        private String cachedSourceContent;
        private boolean sharedSessionEnabled = true;
        private int maxRetries = 0;
        private SharedCircuitBreaker reviewCircuitBreaker;

        public Builder invocationTimestamp(String invocationTimestamp) {
            this.invocationTimestamp = invocationTimestamp;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder outputConstraints(String outputConstraints) {
            this.outputConstraints = outputConstraints;
            return this;
        }

        public Builder cachedSourceContent(String cachedSourceContent) {
            this.cachedSourceContent = cachedSourceContent;
            return this;
        }

        public Builder sharedSessionEnabled(boolean sharedSessionEnabled) {
            this.sharedSessionEnabled = sharedSessionEnabled;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder reviewCircuitBreaker(SharedCircuitBreaker reviewCircuitBreaker) {
            this.reviewCircuitBreaker = reviewCircuitBreaker;
            return this;
        }

        public ReviewContext build() {
            return new ReviewContext(invocationTimestamp, reasoningEffort, outputConstraints,
                cachedSourceContent, sharedSessionEnabled, maxRetries, reviewCircuitBreaker);
        }
    }

    @Override
    public String toString() {
        return "ReviewContext{timestamp='%s', sharedSession=%b, maxRetries=%d, hasSource=%b}"
            .formatted(invocationTimestamp, sharedSessionEnabled, maxRetries, cachedSourceContent != null);
    }
}
