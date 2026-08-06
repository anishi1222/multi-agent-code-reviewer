package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.shared.ExecutorUtils;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/// Application-layer value object holding the executor resources for a review run.
///
/// Purified from {@code orchestrator.ExecutorResources}:
/// - Fixed import of {@code ExecutorUtils} to {@code shared.ExecutorUtils}.
public record ExecutorResources(
    ExecutorService agentExecutionExecutor,
    Semaphore concurrencyLimit
) {

    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60;

    public ExecutorResources {
        Objects.requireNonNull(agentExecutionExecutor, "agentExecutionExecutor must not be null");
        Objects.requireNonNull(concurrencyLimit, "concurrencyLimit must not be null");
    }

    public void shutdownGracefully() {
        ExecutorUtils.shutdownGracefully(agentExecutionExecutor, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
    }
}
