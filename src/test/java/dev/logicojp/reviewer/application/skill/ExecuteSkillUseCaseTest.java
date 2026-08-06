package dev.logicojp.reviewer.application.skill;

import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillParameter;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecuteSkillUseCase")
class ExecuteSkillUseCaseTest {

    @Test
    @DisplayName("存在しないスキルIDは失敗結果を返す")
    void unknownSkillReturnsFailureResult() {
        ExecuteSkillUseCase useCase = new ExecuteSkillUseCase(_ -> "unused");

        SkillResult result = useCase.execute("missing", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Unknown skill: missing");
    }

    @Test
    @DisplayName("登録済みスキルはパラメータを展開してCopilotセッションへ渡す")
    void executeRegisteredSkillWithResolvedPrompt() {
        SkillDefinition skill = new SkillDefinition(
            "id-1",
            "name",
            "desc",
            "hello ${name}",
            List.of(SkillParameter.required("name", "name")),
            Map.of()
        );
        AtomicReference<SessionRequest> capturedRequest = new AtomicReference<>();
        RunCopilotSessionPort runner = request -> {
            capturedRequest.set(request);
            return "ok";
        };
        ExecuteSkillUseCase useCase = new ExecuteSkillUseCase(
            runner,
            id -> "id-1".equals(id) ? Optional.of(skill) : Optional.empty(),
            () -> List.of(skill),
            "model"
        );

        SkillResult result = useCase.execute("id-1", Map.of("name", "Copilot"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("ok");
        assertThat(capturedRequest.get().prompt()).isEqualTo("hello Copilot");
        assertThat(capturedRequest.get().agentConfig().name()).isEqualTo("skill-id-1");
        assertThat(capturedRequest.get().agentConfig().model()).isEqualTo("model");
    }

    @Test
    @DisplayName("登録済みスキルを一覧できる")
    void listSkillsReturnsSuppliedDefinitions() {
        SkillDefinition skill = SkillDefinition.of("id-1", "name", "desc", "prompt");
        ExecuteSkillUseCase useCase = new ExecuteSkillUseCase(
            _ -> "unused",
            id -> "id-1".equals(id) ? Optional.of(skill) : Optional.empty(),
            () -> List.of(skill),
            "model"
        );

        assertThat(useCase.listSkills()).containsExactly(skill);
    }

    // not ported: registering skills from AgentConfig moved out of ExecuteSkillUseCase; it only consumes lookup/listing ports.
}
