package dev.logicojp.reviewer.presentation.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LifecycleRunner")
class LifecycleRunnerTest {

    @Nested
    @DisplayName("executeWithLifecycle")
    class ExecuteWithLifecycle {

        @Test
        @DisplayName("初期化→実行→シャットダウンの順で実行される")
        void executesInOrder() {
            var order = new AtomicInteger(0);
            var initOrder = new AtomicInteger();
            var execOrder = new AtomicInteger();
            var shutdownOrder = new AtomicInteger();

            int result = LifecycleRunner.executeWithLifecycle(
                () -> initOrder.set(order.incrementAndGet()),
                () -> { execOrder.set(order.incrementAndGet()); return 42; },
                () -> shutdownOrder.set(order.incrementAndGet())
            );

            assertThat(initOrder.get()).isEqualTo(1);
            assertThat(execOrder.get()).isEqualTo(2);
            assertThat(shutdownOrder.get()).isEqualTo(3);
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("executorの戻り値を返す")
        void returnsExecutorResult() {
            int result = LifecycleRunner.executeWithLifecycle(
                () -> {},
                () -> 99,
                () -> {}
            );

            assertThat(result).isEqualTo(99);
        }

        @Test
        @DisplayName("初期化で例外が発生してもシャットダウンが実行される")
        void shutdownRunsOnInitializerFailure() {
            var shutdownRan = new AtomicBoolean(false);

            assertThatThrownBy(() ->
                LifecycleRunner.executeWithLifecycle(
                    () -> { throw new RuntimeException("init failed"); },
                    () -> 0,
                    () -> shutdownRan.set(true)
                )
            ).isInstanceOf(RuntimeException.class)
             .hasMessage("init failed");

            assertThat(shutdownRan.get()).isTrue();
        }

        @Test
        @DisplayName("実行で例外が発生してもシャットダウンが実行される")
        void shutdownRunsOnExecutorFailure() {
            var shutdownRan = new AtomicBoolean(false);

            assertThatThrownBy(() ->
                LifecycleRunner.executeWithLifecycle(
                    () -> {},
                    () -> { throw new RuntimeException("exec failed"); },
                    () -> shutdownRan.set(true)
                )
            ).isInstanceOf(RuntimeException.class)
             .hasMessage("exec failed");

            assertThat(shutdownRan.get()).isTrue();
        }
    }
}
