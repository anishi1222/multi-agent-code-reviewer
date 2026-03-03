package dev.logicojp.reviewer.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutorResources")
class ExecutorResourcesTest {

    @Test
    @DisplayName("必須リソースがnullの場合は例外を投げる")
    void throwsWhenRequiredResourcesAreNull() {
        assertThatThrownBy(() -> new ExecutorResources(null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("shutdownGracefullyでリソースを停止できる")
    void shutdownGracefullyClosesExecutors() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var agentExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var scheduler = Executors.newSingleThreadScheduledExecutor();

        var resources = new ExecutorResources(executor, agentExecutor, scheduler, new Semaphore(1));
        resources.shutdownGracefully();

        assertThat(executor.isShutdown()).isTrue();
        assertThat(agentExecutor.isShutdown()).isTrue();
        assertThat(scheduler.isShutdown()).isTrue();
    }
}
