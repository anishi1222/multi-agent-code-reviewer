package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/// Configuration for skill file loading and execution tuning.
@ConfigurationProperties("reviewer.skills")
public record SkillConfig(
    @Nullable String filename,
    @Nullable String directory,
    int maxParameterValueLength,
    int maxExecutorCacheSize,
    int executorCacheInitialCapacity,
    double executorCacheLoadFactor,
    int serviceShutdownTimeoutSeconds,
    int executorShutdownTimeoutSeconds
) {

    private static final String DEFAULT_FILENAME = "SKILL.md";
    private static final String DEFAULT_DIRECTORY = ".github/skills";
    public static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = 10_000;
    public static final int DEFAULT_MAX_EXECUTOR_CACHE_SIZE = 16;
    public static final int DEFAULT_EXECUTOR_CACHE_INITIAL_CAPACITY = 16;
    public static final double DEFAULT_EXECUTOR_CACHE_LOAD_FACTOR = 0.75;
    public static final int DEFAULT_SERVICE_SHUTDOWN_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 30;

    /// Creates a SkillConfig with all default values.
    public static SkillConfig defaults() {
        return new SkillConfig(
            DEFAULT_FILENAME,
            DEFAULT_DIRECTORY,
            DEFAULT_MAX_PARAMETER_VALUE_LENGTH,
            DEFAULT_MAX_EXECUTOR_CACHE_SIZE,
            DEFAULT_EXECUTOR_CACHE_INITIAL_CAPACITY,
            DEFAULT_EXECUTOR_CACHE_LOAD_FACTOR,
            DEFAULT_SERVICE_SHUTDOWN_TIMEOUT_SECONDS,
            DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS
        );
    }

    public SkillConfig {
        filename = ConfigDefaults.defaultIfBlank(filename, DEFAULT_FILENAME);
        directory = ConfigDefaults.defaultIfBlank(directory, DEFAULT_DIRECTORY);
        maxParameterValueLength = ConfigDefaults.defaultIfNonPositive(
            maxParameterValueLength, DEFAULT_MAX_PARAMETER_VALUE_LENGTH);
        maxExecutorCacheSize = ConfigDefaults.defaultIfNonPositive(
            maxExecutorCacheSize, DEFAULT_MAX_EXECUTOR_CACHE_SIZE);
        executorCacheInitialCapacity = ConfigDefaults.defaultIfNonPositive(
            executorCacheInitialCapacity, DEFAULT_EXECUTOR_CACHE_INITIAL_CAPACITY);
        if (executorCacheLoadFactor <= 0.0) {
            executorCacheLoadFactor = DEFAULT_EXECUTOR_CACHE_LOAD_FACTOR;
        }
        serviceShutdownTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(
            serviceShutdownTimeoutSeconds, DEFAULT_SERVICE_SHUTDOWN_TIMEOUT_SECONDS);
        executorShutdownTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(
            executorShutdownTimeoutSeconds, DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
    }
}
