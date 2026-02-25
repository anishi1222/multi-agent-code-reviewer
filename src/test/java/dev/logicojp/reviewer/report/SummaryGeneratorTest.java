package dev.logicojp.reviewer.report;

import org.assertj.core.api.Assertions;
import com.github.copilot.sdk.CopilotClient;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig.SummarySettings;
import dev.logicojp.reviewer.config.ResilienceConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.ApiCircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;


@DisplayName("SummaryGenerator")
class SummaryGeneratorTest {

    @Nested
    @DisplayName("clipContent")
    class ClipContent {

        @Test
        @DisplayName("上限超過時に切り詰めマーカーを付与する")
        void truncatesAndAppendsMarker(@TempDir Path tempDir) throws Exception {
            try (var fixture = newFixture(tempDir, summarySettings(20, 100, 60))) {
                String content = "abcdefghijklmnopqrstuvwxyz";

                String clipped = (String) invokeInstance(
                    fixture.generator,
                    "clipContent",
                    new Class[]{String.class, int.class, int.class},
                    content,
                    10,
                    8
                );

                Assertions.assertThat(clipped)
                    .startsWith("abcdefgh")
                    .contains("... (truncated for summary)");
            }
        }
    }

    @Nested
    @DisplayName("buildSummaryPrompt")
    class BuildSummaryPrompt {

        @Test
        @DisplayName("maxTotalPromptContentを超えないように内容を切り詰める")
        void truncatesByTotalPromptLimit(@TempDir Path tempDir) throws Exception {
            try (var fixture = newFixture(tempDir, summarySettings(30, 25, 60))) {
                var results = List.of(
                    successResult("agent-a", "AAAAAAAAAAAAAAAAAAAA"),
                    successResult("agent-b", "BBBBBBBBBBBBBBBBBBBB")
                );

                String prompt = (String) invokeInstance(
                    fixture.generator,
                    "buildSummaryPrompt",
                    new Class[]{List.class, String.class},
                    results,
                    "owner/repo"
                );

                Assertions.assertThat(prompt).contains("owner/repo");
                Assertions.assertThat(prompt).contains("agent-a");
                Assertions.assertThat(prompt).contains("agent-b");
                Assertions.assertThat(prompt).contains("... (truncated for summary)");
            }
        }
    }

    @Nested
    @DisplayName("buildSummaryWithAI")
    class BuildSummaryWithAI {

        @Test
        @DisplayName("サーキットブレーカーopen時はフォールバックサマリーを返す")
        void returnsFallbackWhenCircuitBreakerOpen(@TempDir Path tempDir) throws Exception {
            try (var fixture = newFixture(tempDir, summarySettings(50, 200, 80))) {
                forceCircuitOpen(fixture.generator);
                var results = List.of(
                    successResult("agent-a", "issue details"),
                    failureResult("agent-b", "timeout")
                );

                String summary = (String) invokeInstance(
                    fixture.generator,
                    "buildSummaryWithAI",
                    new Class[]{List.class, String.class},
                    results,
                    "owner/repo"
                );

                Assertions.assertThat(summary).contains("生成タイムアウトのため、簡易サマリーを出力します。");
                Assertions.assertThat(summary).contains("agent-a");
                Assertions.assertThat(summary).contains("agent-b");
            }
        }
    }

    private static void forceCircuitOpen(SummaryGenerator generator) throws Exception {
        Field field = SummaryGenerator.class.getDeclaredField("apiCircuitBreaker");
        field.setAccessible(true);
        ApiCircuitBreaker breaker = (ApiCircuitBreaker) field.get(generator);
        breaker.recordFailure();
    }

    private static ReviewResult successResult(String name, String content) {
        return ReviewResult.builder()
            .agentConfig(agentConfig(name))
            .repository("owner/repo")
            .content(content)
            .success(true)
            .build();
    }

    private static ReviewResult failureResult(String name, String errorMessage) {
        return ReviewResult.builder()
            .agentConfig(agentConfig(name))
            .repository("owner/repo")
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(
            name,
            name,
            "gpt-5",
            "system",
            "instruction",
            "output",
            List.of("focus"),
            List.of()
        );
    }

    private static SummarySettings summarySettings(int maxPerAgent, int maxTotal, int excerptLength) {
        return new SummarySettings(maxPerAgent, maxTotal, excerptLength, 128, 64, 2);
    }

    private static SummaryGeneratorFixture newFixture(Path tempDir, SummarySettings settings) {
        Path outputDir = tempDir.resolve("reports").resolve("2026-02-24-13-15-18");
        var config = new SummaryGenerator.SummaryConfig(
            outputDir,
            "gpt-5",
            null,
            1,
            settings,
            new ResilienceConfig.OperationSettings(1, 30, 2, 1, 2)
        );
        var templateService = new TemplateService(new TemplateConfig(null, null, null, null, null, null, null, null));
        var client = new CopilotClient();
        var generator = new SummaryGenerator(config, client, templateService, Clock.systemUTC());
        return new SummaryGeneratorFixture(generator, client);
    }

    private static Object invokeInstance(Object target,
                                         String methodName,
                                         Class<?>[] parameterTypes,
                                         Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private record SummaryGeneratorFixture(SummaryGenerator generator, CopilotClient client)
        implements AutoCloseable {

        @Override
        public void close() {
            client.close();
        }
    }
}
