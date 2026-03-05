package dev.logicojp.reviewer.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillExecutor")
class SkillExecutorTest {

    @Test
    @DisplayName("必須パラメータ不足時は失敗結果を返す")
    void returnsFailureWhenRequiredParameterMissing() {
        SkillDefinition skill = new SkillDefinition(
            "s1",
            "skill",
            "desc",
            "hello ${name}",
            List.of(SkillParameter.required("name", "name")),
            Map.of()
        );

        SkillExecutor executor = new SkillExecutor(
            null,
            null,
            null,
            new SkillExecutor.SkillExecutorConfig("model", 1, 10_000, 30),
            Runnable::run,
            false
        );

        SkillResult result = executor.execute(skill, Map.of()).join();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Missing required parameter");
    }

    @Test
    @DisplayName("ownsExecutor=true の場合 close でExecutorServiceを停止する")
    void closeStopsOwnedExecutorService() {
        ExecutorService es = Executors.newSingleThreadExecutor();
        SkillExecutor executor = new SkillExecutor(
            null,
            null,
            null,
            new SkillExecutor.SkillExecutorConfig("model", 1, 10_000, 30),
            es,
            true
        );

        executor.close();

        assertThat(es.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("ownsExecutor=false の場合 close で外部ExecutorServiceを停止しない")
    void closeDoesNotStopExternalExecutorService() throws InterruptedException {
        ExecutorService es = Executors.newSingleThreadExecutor();
        SkillExecutor executor = new SkillExecutor(
            null,
            null,
            null,
            new SkillExecutor.SkillExecutorConfig("model", 1, 10_000, 30),
            es,
            false
        );

        executor.close();

        assertThat(es.isShutdown()).isFalse();
        es.shutdownNow();
        es.awaitTermination(1, TimeUnit.SECONDS);
    }
}
