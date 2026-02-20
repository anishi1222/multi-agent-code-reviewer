package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Resilience settings for circuit breakers and retries.
/// Bound to `reviewer.resilience` in YAML.
@ConfigurationProperties("reviewer.resilience")
public record ResilienceConfig(
    OperationSettings review,
    OperationSettings summary,
    OperationSettings skill
) {

    public ResilienceConfig {
        review = review != null ? review : OperationSettings.reviewDefaults();
        summary = summary != null ? summary : OperationSettings.summaryDefaults();
        skill = skill != null ? skill : OperationSettings.skillDefaults();
    }

    @ConfigurationProperties("operation")
    public record OperationSettings(
        int failureThreshold,
        long openDurationSeconds,
        int maxAttempts,
        long backoffBaseMs,
        long backoffMaxMs
    ) {
        public static final int DEFAULT_REVIEW_FAILURE_THRESHOLD = 5;
        public static final long DEFAULT_REVIEW_OPEN_DURATION_SECONDS = 30;
        public static final int DEFAULT_REVIEW_MAX_ATTEMPTS = 3;
        public static final long DEFAULT_REVIEW_BACKOFF_BASE_MS = 1000;
        public static final long DEFAULT_REVIEW_BACKOFF_MAX_MS = 8000;

        public static final int DEFAULT_SUMMARY_FAILURE_THRESHOLD = 3;
        public static final long DEFAULT_SUMMARY_OPEN_DURATION_SECONDS = 20;
        public static final int DEFAULT_SUMMARY_MAX_ATTEMPTS = 3;
        public static final long DEFAULT_SUMMARY_BACKOFF_BASE_MS = 500;
        public static final long DEFAULT_SUMMARY_BACKOFF_MAX_MS = 4000;

        public static final int DEFAULT_SKILL_FAILURE_THRESHOLD = 3;
        public static final long DEFAULT_SKILL_OPEN_DURATION_SECONDS = 20;
        public static final int DEFAULT_SKILL_MAX_ATTEMPTS = 3;
        public static final long DEFAULT_SKILL_BACKOFF_BASE_MS = 500;
        public static final long DEFAULT_SKILL_BACKOFF_MAX_MS = 4000;

        public OperationSettings {
            failureThreshold = ConfigDefaults.defaultIfNonPositive(failureThreshold, DEFAULT_REVIEW_FAILURE_THRESHOLD);
            openDurationSeconds = ConfigDefaults.defaultIfNonPositive(openDurationSeconds, DEFAULT_REVIEW_OPEN_DURATION_SECONDS);
            maxAttempts = ConfigDefaults.defaultIfNonPositive(maxAttempts, DEFAULT_REVIEW_MAX_ATTEMPTS);
            backoffBaseMs = ConfigDefaults.defaultIfNonPositive(backoffBaseMs, DEFAULT_REVIEW_BACKOFF_BASE_MS);
            backoffMaxMs = ConfigDefaults.defaultIfNonPositive(backoffMaxMs, DEFAULT_REVIEW_BACKOFF_MAX_MS);
            if (backoffMaxMs < backoffBaseMs) {
                backoffMaxMs = backoffBaseMs;
            }
        }

        public static OperationSettings reviewDefaults() {
            return new OperationSettings(
                DEFAULT_REVIEW_FAILURE_THRESHOLD,
                DEFAULT_REVIEW_OPEN_DURATION_SECONDS,
                DEFAULT_REVIEW_MAX_ATTEMPTS,
                DEFAULT_REVIEW_BACKOFF_BASE_MS,
                DEFAULT_REVIEW_BACKOFF_MAX_MS);
        }

        public static OperationSettings summaryDefaults() {
            return new OperationSettings(
                DEFAULT_SUMMARY_FAILURE_THRESHOLD,
                DEFAULT_SUMMARY_OPEN_DURATION_SECONDS,
                DEFAULT_SUMMARY_MAX_ATTEMPTS,
                DEFAULT_SUMMARY_BACKOFF_BASE_MS,
                DEFAULT_SUMMARY_BACKOFF_MAX_MS);
        }

        public static OperationSettings skillDefaults() {
            return new OperationSettings(
                DEFAULT_SKILL_FAILURE_THRESHOLD,
                DEFAULT_SKILL_OPEN_DURATION_SECONDS,
                DEFAULT_SKILL_MAX_ATTEMPTS,
                DEFAULT_SKILL_BACKOFF_BASE_MS,
                DEFAULT_SKILL_BACKOFF_MAX_MS);
        }
    }
}