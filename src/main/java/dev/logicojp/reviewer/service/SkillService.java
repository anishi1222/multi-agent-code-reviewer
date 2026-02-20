package dev.logicojp.reviewer.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.ReviewerConfig;
import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillExecutor;
import dev.logicojp.reviewer.skill.SkillRegistry;
import dev.logicojp.reviewer.util.TokenHashUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;

/// Service for managing and executing skills.
@Singleton
public class SkillService {

    private final SkillRegistry skillRegistry;
    private final CopilotService copilotService;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    private final ReviewerConfig.Skills skillsConfig;
    private final Cache<ExecutorCacheKey, SkillExecutor> executorCache;

    @Inject
    public SkillService(SkillRegistry skillRegistry,
                        CopilotService copilotService,
                        GithubMcpConfig githubMcpConfig,
                        ExecutionConfig executionConfig,
                        ReviewerConfig reviewerConfig) {
        this.skillRegistry = skillRegistry;
        this.copilotService = copilotService;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        this.skillsConfig = reviewerConfig.skills();
        this.executorCache = Caffeine.newBuilder()
            .initialCapacity(skillsConfig.executorCacheInitialCapacity())
            .maximumSize(skillsConfig.maxExecutorCacheSize())
            .build();
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
    public SkillExecutor.Result executeSkill(String skillId,
                                             Map<String, String> parameters,
                                             String githubToken,
                                             String model) {
        return executeResolvedSkill(skillId,
            skill -> createExecutor(githubToken, model).execute(skill, parameters));
    }

    /// Executes a skill with a custom system prompt.
    public SkillExecutor.Result executeSkill(String skillId,
                                             Map<String, String> parameters,
                                             String githubToken,
                                             String model,
                                             String systemPrompt) {
        return executeResolvedSkill(skillId,
            skill -> createExecutor(githubToken, model).execute(skill, parameters, systemPrompt));
    }

    private SkillExecutor.Result executeResolvedSkill(
            String skillId,
            java.util.function.Function<SkillDefinition, SkillExecutor.Result> runner) {
        Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
        if (skillOpt.isEmpty()) {
            return SkillExecutor.Result.failure(skillId, "Skill not found: " + skillId);
        }
        return runner.apply(skillOpt.get());
    }

    private SkillExecutor createExecutor(String githubToken, String model) {
        var key = new ExecutorCacheKey(
            TokenHashUtils.sha256HexOrEmpty(githubToken),
            model);
        return executorCache.get(key, _ -> new SkillExecutor(
            copilotService.getClient(),
            githubToken,
            githubMcpConfig,
            new SkillExecutor.Config(
                model,
                executionConfig.skillTimeoutMinutes(),
                skillsConfig.maxParameterValueLength(),
                skillsConfig.executorShutdownTimeoutSeconds())));
    }

    private record ExecutorCacheKey(String tokenDigest, String model) {
        @Override
        public String toString() {
            return "ExecutorCacheKey{tokenDigest=***, model='%s'}".formatted(model);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorCache.invalidateAll();
    }
}
