package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutorUtils")
class ExecutorUtilsTest {

    @Nested
    @DisplayName("shutdownGracefully")
    class ShutdownGracefully {

        @Test
        @DisplayName("nullのExecutorServiceを渡してもエラーにならない")
        void nullExecutorDoesNotThrow() {
            ExecutorUtils.shutdownGracefully(null, 10);
        }

        @Test
        @DisplayName("正常なExecutorServiceをシャットダウンできる")
        void shutsDownNormally() throws InterruptedException {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            AtomicBoolean ran = new AtomicBoolean(false);
            executor.submit(() -> ran.set(true));
            Thread.sleep(50);
            ExecutorUtils.shutdownGracefully(executor, 5);
            assertThat(executor.isShutdown()).isTrue();
            assertThat(ran.get()).isTrue();
        }

        @Test
        @DisplayName("タイムアウト後に強制シャットダウンされる")
        void forcesShutdownAfterTimeout() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });
            ExecutorUtils.shutdownGracefully(executor, 1);
            assertThat(executor.isShutdown()).isTrue();
        }
    }
}
