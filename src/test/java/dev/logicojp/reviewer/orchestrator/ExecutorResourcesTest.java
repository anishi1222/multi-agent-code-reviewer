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
        assertThatThrownBy(() -> new ExecutorResources(null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("shutdownGracefullyでリソースを停止できる")
    void shutdownGracefullyClosesExecutors() {
        var agentExecutor = Executors.newVirtualThreadPerTaskExecutor();

        var resources = new ExecutorResources(agentExecutor, new Semaphore(1));
        resources.shutdownGracefully();

        assertThat(agentExecutor.isShutdown()).isTrue();
    }
}
