package dev.logicojp.reviewer.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
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
import dev.logicojp.reviewer.util.ExecutorUtils;
import dev.logicojp.reviewer.util.TokenHashUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService executorService;
    private final Cache<ExecutorCacheKey, SkillExecutor> executorCache;

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
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.executorCache = Caffeine.newBuilder()
            .initialCapacity(skillConfig.executorCacheInitialCapacity())
            .maximumSize(skillConfig.maxExecutorCacheSize())
            .removalListener((ExecutorCacheKey key, SkillExecutor executor, RemovalCause cause) -> {
                if (executor != null && cause.wasEvicted()) {
                    executor.close();
                }
            })
            .build();
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
    public CompletableFuture<SkillResult> executeSkill(String skillId,
                                                        Map<String, String> parameters,
                                                        String githubToken,
                                                        String model) {
        return executeResolvedSkill(
            skillId,
            skill -> createExecutor(githubToken, model).execute(skill, parameters)
        );
    }

    /// Executes a skill with a custom system prompt.
    public CompletableFuture<SkillResult> executeSkill(String skillId,
                                                        Map<String, String> parameters,
                                                        String githubToken,
                                                        String model,
                                                        String systemPrompt) {
        return executeResolvedSkill(
            skillId,
            skill -> createExecutor(githubToken, model).execute(skill, parameters, systemPrompt)
        );
    }

    private CompletableFuture<SkillResult> executeResolvedSkill(
            String skillId,
            Function<SkillDefinition, CompletableFuture<SkillResult>> runner) {
        Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
        if (skillOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                SkillResult.failure(skillId, "Skill not found: " + skillId));
        }

        return runner.apply(skillOpt.get());
    }

    private SkillExecutor createExecutor(String githubToken, String model) {
        var key = new ExecutorCacheKey(
            TokenHashUtils.sha256HexOrEmpty(githubToken),
            model
        );
        return executorCache.get(key, _ -> new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            new SkillExecutor.SkillExecutorConfig(
                model,
                executionConfig.skillTimeoutMinutes(),
                skillConfig.maxParameterValueLength(),
                skillConfig.executorShutdownTimeoutSeconds()
            ),
            executorService,
            false,
            circuitBreaker
        ));
    }

    private record ExecutorCacheKey(String tokenDigest, String model) {
        @Override
        public String toString() {
            return "ExecutorCacheKey{tokenDigest=***, model='%s'}".formatted(model);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (SkillExecutor executor : executorCache.asMap().values()) {
            executor.close();
        }
        executorCache.invalidateAll();
        ExecutorUtils.shutdownGracefully(executorService, skillConfig.serviceShutdownTimeoutSeconds());
    }
}
