package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.util.ExecutorUtils;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

record ExecutorResources(
    @Nullable ExecutorService executorService,
    ExecutorService agentExecutionExecutor,
    ScheduledExecutorService sharedScheduler,
    Semaphore concurrencyLimit
) {
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60;
    private static final int SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 10;

    ExecutorResources {
        agentExecutionExecutor = Objects.requireNonNull(agentExecutionExecutor);
        sharedScheduler = Objects.requireNonNull(sharedScheduler);
        concurrencyLimit = Objects.requireNonNull(concurrencyLimit);
    }

    void shutdownGracefully() {
        ExecutorUtils.shutdownGracefully(executorService, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        ExecutorUtils.shutdownGracefully(agentExecutionExecutor, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        ExecutorUtils.shutdownGracefully(sharedScheduler, SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
    }
}