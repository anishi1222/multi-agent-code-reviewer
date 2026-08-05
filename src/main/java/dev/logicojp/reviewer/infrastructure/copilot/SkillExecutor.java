package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import dev.logicojp.reviewer.infrastructure.parsing.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Infrastructure adapter that implements {@link ExecuteSkillPort}.
///
/// Bridges the application inbound port to the infrastructure outbound port
/// ({@link RunCopilotSessionPort}), using {@link SkillRegistry} for skill lookups.
///
/// Replaces brownfield {@code skill.SkillExecutor} which used {@code CopilotClient}
/// directly. The new version delegates session execution to {@link RunCopilotSessionPort}.
///
/// No DI annotations — instantiated by the presentation layer factory
/// (or {@link ReviewOrchestratorFactory} when skill access is needed).
public class SkillExecutor implements ExecuteSkillPort {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutor.class);

    private final RunCopilotSessionPort runCopilotSession;
    private final SkillRegistry skillRegistry;
    private final String defaultModel;
    private final List<McpServerSpec> mcpServers;

    public SkillExecutor(RunCopilotSessionPort runCopilotSession,
                         SkillRegistry skillRegistry,
                         String defaultModel,
                         List<McpServerSpec> mcpServers) {
        this.runCopilotSession = Objects.requireNonNull(runCopilotSession);
        this.skillRegistry = Objects.requireNonNull(skillRegistry);
        this.defaultModel = defaultModel != null && !defaultModel.isBlank()
            ? defaultModel : "claude-sonnet-4.5";
        this.mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    @Override
    public SkillResult execute(String skillId, Map<String, String> parameters) {
        Optional<SkillDefinition> maybeSkill = skillRegistry.get(skillId);
        if (maybeSkill.isEmpty()) {
            logger.warn("SkillExecutor: skill '{}' not found in registry", skillId);
            return SkillResult.failure(skillId, "Unknown skill: " + skillId);
        }
        SkillDefinition skill = maybeSkill.get();
        return executeSkill(skill, parameters);
    }

    @Override
    public List<SkillDefinition> listSkills() {
        return skillRegistry.getAll();
    }

    private SkillResult executeSkill(SkillDefinition skill, Map<String, String> params) {
        String resolvedPrompt = skill.buildPrompt(params, 4096);
        if (resolvedPrompt == null || resolvedPrompt.isBlank()) {
            logger.warn("SkillExecutor: skill '{}' rendered an empty prompt", skill.id());
            return SkillResult.failure(skill.id(), "Skill prompt is empty after parameter substitution");
        }

        AgentConfig agentConfig = AgentConfig.builder()
            .name("skill-" + skill.id())
            .model(defaultModel)
            .build();

        var request = new SessionRequest(agentConfig, resolvedPrompt, mcpServers, Map.of());

        try {
            logger.info("SkillExecutor: executing skill '{}' via RunCopilotSessionPort", skill.id());
            String response = runCopilotSession.runSession(request);
            if (response == null || response.isBlank()) {
                logger.warn("SkillExecutor: empty response from Copilot for skill '{}'", skill.id());
                return SkillResult.failure(skill.id(), "Empty response from Copilot");
            }
            logger.info("SkillExecutor: skill '{}' completed successfully ({} chars)",
                skill.id(), response.length());
            return SkillResult.success(skill.id(), response);
        } catch (Exception e) {
            logger.error("SkillExecutor: skill '{}' execution failed: {}", skill.id(), e.getMessage(), e);
            return SkillResult.failure(skill.id(), "Skill execution failed: " + e.getMessage());
        }
    }
}
