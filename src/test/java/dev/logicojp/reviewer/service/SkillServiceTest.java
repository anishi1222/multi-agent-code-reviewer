package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.SkillConfig;
import dev.logicojp.reviewer.skill.SkillDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillService")
class SkillServiceTest {

    private static CopilotService newCopilotService() {
        return new CopilotService(
            new CopilotCliPathResolver(),
            new CopilotCliHealthChecker(new CopilotTimeoutResolver()),
            new CopilotTimeoutResolver(),
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
    }

    @Test
    @DisplayName("存在しないスキルIDは失敗結果を返す")
    void unknownSkillReturnsFailureResult() {
        SkillService service = new SkillService(
            new dev.logicojp.reviewer.skill.SkillRegistry(),
            newCopilotService(),
            new GithubMcpConfig(null, null, null, null, null, null),
            ExecutionConfig.ofFlat(1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0),
            SkillConfig.defaults(),
            SharedCircuitBreaker.withDefaultConfig()
        );

        var result = service.executeSkill("missing", Map.of(), null, "model").join();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Skill not found");
    }

    @Test
    @DisplayName("エージェント定義からスキルを登録できる")
    void registerAgentSkillsAddsToRegistry() {
        SkillService service = new SkillService(
            new dev.logicojp.reviewer.skill.SkillRegistry(),
            newCopilotService(),
            new GithubMcpConfig(null, null, null, null, null, null),
            ExecutionConfig.ofFlat(1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0),
            SkillConfig.defaults(),
            SharedCircuitBreaker.withDefaultConfig()
        );

        SkillDefinition skill = SkillDefinition.of("id-1", "name", "desc", "prompt");
        AgentConfig agent = new AgentConfig(
            "agent", "Agent", "model", "system", "instruction", null, List.of(), List.of(skill)
        );

        service.registerAgentSkills(agent);

        assertThat(service.getSkill("id-1")).isPresent();
    }
}
