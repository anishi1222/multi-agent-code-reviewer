package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillParameter;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import dev.logicojp.reviewer.infrastructure.parsing.SkillRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillExecutor")
class SkillExecutorTest {

    @Test
    @DisplayName("未知のスキルIDは失敗結果を返す")
    void returnsFailureWhenSkillIsUnknown() {
        SkillExecutor executor = new SkillExecutor(
            request -> "unused",
            new SkillRegistry(),
            "model",
            List.of()
        );

        SkillResult result = executor.execute("missing", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Unknown skill: missing");
    }

    @Test
    @DisplayName("登録済みスキルはパラメータを展開してCopilotセッションへ渡す")
    void executesRegisteredSkillWithResolvedPrompt() {
        SkillDefinition skill = new SkillDefinition(
            "s1",
            "skill",
            "desc",
            "hello ${name}",
            List.of(SkillParameter.required("name", "name")),
            Map.of()
        );
        var registry = new SkillRegistry();
        registry.register(skill);
        AtomicReference<String> prompt = new AtomicReference<>();
        RunCopilotSessionPort runner = request -> {
            prompt.set(request.prompt());
            return "ok";
        };
        SkillExecutor executor = new SkillExecutor(runner, registry, "model", List.of());

        SkillResult result = executor.execute("s1", Map.of("name", "Copilot"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("ok");
        assertThat(prompt).hasValue("hello Copilot");
    }

    // removed: missing required parameter validation is no longer performed by SkillExecutor; SkillDefinition exposes validateParameters for callers that need pre-validation.
    // removed: SkillExecutor is no longer AutoCloseable and has no close lifecycle to verify.
}
