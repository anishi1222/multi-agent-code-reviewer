package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.util.ExecutorUtils;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

record ExecutorResources(
    ExecutorService agentExecutionExecutor,
    Semaphore concurrencyLimit
) {
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60;

    ExecutorResources {
        agentExecutionExecutor = Objects.requireNonNull(agentExecutionExecutor);
        concurrencyLimit = Objects.requireNonNull(concurrencyLimit);
    }

    void shutdownGracefully() {
        ExecutorUtils.shutdownGracefully(agentExecutionExecutor, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
    }
}