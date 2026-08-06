package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillExecutionPreparation")
class SkillExecutionPreparationTest {

    // removed: wrapsIoExceptionFromAgentLoad - LoadAgentPort no longer declares checked IOException, so wrapping is not part of this API.

    @Test
    @DisplayName("--list指定時はlistOnlyで返す")
    void returnsListOnlyWhenListOptionEnabled() {
        AtomicBoolean loaded = new AtomicBoolean(false);
        var service = new SkillExecutionPreparation(
            stubLoadAgentPort(loaded),
            stubExecuteSkillPort(true),
            token -> Optional.of("token")
        );

        SkillExecutionPreparation.PreparationResult result = service.prepare(options(null, true, List.of()));

        assertThat(loaded.get()).isTrue();
        assertThat(result.listOnly()).isTrue();
        assertThat(result.parameters()).isEmpty();
    }

    @Test
    @DisplayName("通常実行時はtokenとparametersを返す")
    void returnsResolvedTokenAndParameters() {
        var service = new SkillExecutionPreparation(
            stubLoadAgentPort(new AtomicBoolean()),
            stubExecuteSkillPort(true),
            token -> Optional.of("resolved-token")
        );

        SkillExecutionPreparation.PreparationResult result = service.prepare(
            options("scan-security", false, List.of("env=prod", "region=jp"))
        );

        assertThat(result.listOnly()).isFalse();
        assertThat(result.resolvedToken()).isEqualTo("resolved-token");
        assertThat(result.parameters()).containsEntry("env", "prod").containsEntry("region", "jp");
    }

    @Test
    @DisplayName("skill ID未指定時はバリデーションエラー")
    void throwsWhenSkillIdMissing() {
        var service = new SkillExecutionPreparation(
            stubLoadAgentPort(new AtomicBoolean()),
            stubExecuteSkillPort(true),
            token -> Optional.of("resolved-token")
        );

        assertThatThrownBy(() -> service.prepare(options(" ", false, List.of())))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Skill ID required");
    }

    @Test
    @DisplayName("skill未存在時はバリデーションエラー")
    void throwsWhenSkillNotFound() {
        var service = new SkillExecutionPreparation(
            stubLoadAgentPort(new AtomicBoolean()),
            stubExecuteSkillPort(false),
            token -> Optional.of("resolved-token")
        );

        assertThatThrownBy(() -> service.prepare(options("unknown", false, List.of())))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Skill not found");
    }

    @Test
    @DisplayName("不正なparameter形式はバリデーションエラー")
    void throwsWhenParameterFormatInvalid() {
        var service = new SkillExecutionPreparation(
            stubLoadAgentPort(new AtomicBoolean()),
            stubExecuteSkillPort(true),
            token -> Optional.of("resolved-token")
        );

        assertThatThrownBy(() -> service.prepare(options("scan-security", false, List.of("invalid"))))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Expected 'key=value'");
    }

    private static SkillOptions options(String skillId, boolean list, List<String> params) {
        return new SkillOptions(
            skillId,
            params,
            null,
            "gpt-5",
            List.of(Path.of("agents")),
            list
        );
    }

    private static LoadAgentPort stubLoadAgentPort(AtomicBoolean loaded) {
        return new LoadAgentPort() {
            @Override
            public List<AgentConfig> loadAll(List<Path> directories) {
                loaded.set(true);
                return List.of(agentConfig("a"));
            }

            @Override
            public Optional<AgentConfig> loadByName(String name, List<Path> directories) {
                return Optional.of(agentConfig(name));
            }
        };
    }

    private static ExecuteSkillPort stubExecuteSkillPort(boolean skillExists) {
        return new ExecuteSkillPort() {
            @Override
            public SkillResult execute(String skillId, Map<String, String> parameters) {
                return SkillResult.success(skillId, "ok");
            }

            @Override
            public List<SkillDefinition> listSkills() {
                return skillExists
                    ? List.of(SkillDefinition.of("scan-security", "Scan Security", "scan", "prompt"))
                    : List.of();
            }
        };
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(name, name, "gpt-5", "prompt", "instruction", "", List.of(), List.of());
    }
}
