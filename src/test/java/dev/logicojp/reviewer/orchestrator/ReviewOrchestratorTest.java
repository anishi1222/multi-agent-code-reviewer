package dev.logicojp.reviewer.orchestrator;

import org.assertj.core.api.Assertions;
import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ResilienceConfig;
import dev.logicojp.reviewer.config.ReviewerConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


@DisplayName("ReviewOrchestrator")
class ReviewOrchestratorTest {

    @Nested
    @DisplayName("perAgentTimeoutMinutes")
    class PerAgentTimeoutMinutes {

        @Test
        @DisplayName("agentTimeout × retryAttempts × passes で算出される")
        void calculatesTimeoutByRetriesAndPasses(@TempDir Path tempDir) throws Exception {
            try (var fixture = orchestratorFixture(tempDir, 2, 3, tempDir.resolve("checkpoints"))) {
                long timeoutMinutes = (long) invokeInstanceMethod(
                    fixture.orchestrator,
                    "perAgentTimeoutMinutes",
                    new Class[]{int.class},
                    2
                );

                Assertions.assertThat(timeoutMinutes).isEqualTo(12L);
            }
        }
    }

    @Nested
    @DisplayName("preComputeSourceContent")
    class PreComputeSourceContent {

        @Test
        @DisplayName("LocalTargetではソース内容を事前収集する")
        void precomputesForLocalTarget(@TempDir Path tempDir) throws Exception {
            Path sourceDir = tempDir.resolve("src");
            Files.createDirectories(sourceDir);
            Files.writeString(sourceDir.resolve("Main.java"), "public class Main {}");

            try (var fixture = orchestratorFixture(tempDir, 1, 2, tempDir.resolve("checkpoints"))) {
                String content = (String) invokeInstanceMethod(
                    fixture.orchestrator,
                    "preComputeSourceContent",
                    new Class[]{ReviewTarget.class},
                    ReviewTarget.local(sourceDir)
                );

                Assertions.assertThat(content).contains("Main.java");
            }
        }

        @Test
        @DisplayName("GitHubTargetでは事前収集しない")
        void returnsNullForGithubTarget(@TempDir Path tempDir) throws Exception {
            try (var fixture = orchestratorFixture(tempDir, 1, 2, tempDir.resolve("checkpoints"))) {
                String content = (String) invokeInstanceMethod(
                    fixture.orchestrator,
                    "preComputeSourceContent",
                    new Class[]{ReviewTarget.class},
                    ReviewTarget.gitHub("owner/repo")
                );

                Assertions.assertThat(content).isNull();
            }
        }
    }

    @Nested
    @DisplayName("asRuntimeException")
    class AsRuntimeException {

        @Test
        @DisplayName("RuntimeExceptionは同一インスタンスを返す")
        void returnsSameRuntimeExceptionInstance() throws Exception {
            RuntimeException input = new IllegalStateException("runtime");

            RuntimeException result = (RuntimeException) invokeStaticMethod(
                ReviewOrchestrator.class,
                "asRuntimeException",
                new Class[]{Throwable.class},
                input
            );

            Assertions.assertThat(result).isSameAs(input);
        }

        @Test
        @DisplayName("checked例外はRuntimeExceptionでラップする")
        void wrapsCheckedException() throws Exception {
            Exception input = new Exception("checked");

            RuntimeException result = (RuntimeException) invokeStaticMethod(
                ReviewOrchestrator.class,
                "asRuntimeException",
                new Class[]{Throwable.class},
                input
            );

            Assertions.assertThat(result).hasCause(input);
        }

        @Test
        @DisplayName("null入力はIllegalStateExceptionを返す")
        void returnsIllegalStateExceptionForNull() throws Exception {
            RuntimeException result = (RuntimeException) invokeStaticMethod(
                ReviewOrchestrator.class,
                "asRuntimeException",
                new Class[]{Throwable.class},
                (Object) null
            );

            Assertions.assertThat(result)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown execution failure");
        }
    }

    private static Object invokeInstanceMethod(Object target,
                                               String methodName,
                                               Class<?>[] parameterTypes,
                                               Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static Object invokeStaticMethod(Class<?> type,
                                             String methodName,
                                             Class<?>[] parameterTypes,
                                             Object... args) throws Exception {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static OrchestratorFixture orchestratorFixture(Path tempDir,
                                                           long agentTimeoutMinutes,
                                                           int retryAttempts,
                                                           Path checkpointDir) {
        var client = new CopilotClient();
        var executionConfig = new ExecutionConfig(
            1,
            1,
            2,
            agentTimeoutMinutes,
            1,
            1,
            1,
            1,
            1,
            1_024,
            512,
            32,
            checkpointDir.toString(),
            null
        );
        var resilience = new ResilienceConfig(
            new ResilienceConfig.OperationSettings(3, 10, retryAttempts, 1, 2),
            null,
            null
        );
        var config = new ReviewOrchestrator.Config(
            null,
            null,
            new ReviewerConfig.LocalFiles(),
            executionConfig,
            List.of(),
            null,
            null,
            new ReviewAgent.PromptTemplates("focus", "header", "result"),
            resilience
        );

        var orchestrator = new ReviewOrchestrator(client, config);
        return new OrchestratorFixture(orchestrator, client);
    }

    private record OrchestratorFixture(ReviewOrchestrator orchestrator, CopilotClient client)
        implements AutoCloseable {

        @Override
        public void close() {
            orchestrator.close();
            client.close();
        }
    }
}
