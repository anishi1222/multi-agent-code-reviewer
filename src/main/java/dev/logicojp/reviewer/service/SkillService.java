package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.CircuitBreakerFactory;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.SkillConfig;
import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillExecutor;
import dev.logicojp.reviewer.skill.SkillRegistry;
import dev.logicojp.reviewer.skill.SkillResult;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// Service for managing and executing skills.
@Singleton
public class SkillService {

    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);

    private final SkillRegistry skillRegistry;
    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    private final SkillConfig skillConfig;
    private final SharedCircuitBreaker circuitBreaker;

    @Inject
    public SkillService(SkillRegistry skillRegistry,
                        CopilotService copilotService,
                        GithubMcpConfig githubMcpConfig,
                        ExecutionConfig executionConfig,
                        SkillConfig skillConfig,
                        CircuitBreakerFactory circuitBreakerFactory) {
        this(skillRegistry, copilotService, githubMcpConfig, executionConfig, skillConfig,
                    circuitBreakerFactory.forSkill());
    }

    SkillService(SkillRegistry skillRegistry,
                     CopilotService copilotService,
                     GithubMcpConfig githubMcpConfig,
                     ExecutionConfig executionConfig,
                     SkillConfig skillConfig,
                     SharedCircuitBreaker circuitBreaker) {
        this.skillRegistry = skillRegistry;
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        this.skillConfig = skillConfig;
        this.circuitBreaker = circuitBreaker;
    }

    /// Registers all skills from an agent configuration.
    public void registerAgentSkills(AgentConfig agentConfig) {
        for (SkillDefinition skill : agentConfig.skills()) {
            skillRegistry.register(skill);
        }
        if (!agentConfig.skills().isEmpty()) {
            logger.info("Registered {} skills from agent: {}",
                agentConfig.skills().size(), agentConfig.name());
        }
    }

    /// Registers multiple agent skills.
    public void registerAllAgentSkills(Map<String, AgentConfig> agents) {
        for (AgentConfig agent : agents.values()) {
            registerAgentSkills(agent);
        }
    }

    /// Gets the skill registry.
    public SkillRegistry getRegistry() {
        return skillRegistry;
    }

    /// Gets a skill by ID.
    public Optional<SkillDefinition> getSkill(String skillId) {
        return skillRegistry.get(skillId);
    }

    /// Executes a skill by ID with the given parameters.
    public SkillResult executeSkill(String skillId,
                                    Map<String, String> parameters,
                                    String githubToken,
                                    String model) {
        return executeResolvedSkill(
            skillId,
            skill -> executeWithExecutor(githubToken, model, executor -> executor.execute(skill, parameters))
        );
    }

    /// Executes a skill with a custom system prompt.
    public SkillResult executeSkill(String skillId,
                                    Map<String, String> parameters,
                                    String githubToken,
                                    String model,
                                    String systemPrompt) {
        return executeResolvedSkill(
            skillId,
            skill -> executeWithExecutor(
                githubToken,
                model,
                executor -> executor.execute(skill, parameters, systemPrompt)
            )
        );
    }

    private SkillResult executeWithExecutor(String githubToken,
                                            String model,
                                            Function<SkillExecutor, SkillResult> runner) {
        // Keep executor/token lifetime bounded to a single skill invocation.
        try (SkillExecutor executor = createExecutor(githubToken, model)) {
            return runner.apply(executor);
        }
    }

    private SkillResult executeResolvedSkill(
            String skillId,
            Function<SkillDefinition, SkillResult> runner) {
        Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
        if (skillOpt.isEmpty()) {
            return SkillResult.failure(skillId, "Skill not found: " + skillId);
        }

        return runner.apply(skillOpt.get());
    }

    private SkillExecutor createExecutor(String githubToken, String model) {
        return new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            new SkillExecutor.SkillExecutorConfig(
                model,
                executionConfig.skillTimeoutMinutes(),
                skillConfig.maxParameterValueLength(),
                skillConfig.executorShutdownTimeoutSeconds()
            ),
            circuitBreaker
        );
    }

    @PreDestroy
    public void shutdown() {
        logger.debug("SkillService shutdown complete");
    }
}
