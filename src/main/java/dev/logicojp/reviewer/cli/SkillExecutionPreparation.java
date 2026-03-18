package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.SkillService;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
class SkillExecutionPreparation {

    @FunctionalInterface
    interface AgentLoader {
        Map<String, AgentConfig> load(List<Path> additionalAgentDirs) throws IOException;
    }

    @FunctionalInterface
    interface SkillRegistrar {
        void register(Map<String, AgentConfig> agents);
    }

    @FunctionalInterface
    interface TokenResolver {
        Optional<String> resolve(String githubToken);
    }

    @FunctionalInterface
    interface SkillLookup {
        boolean exists(String skillId);
    }

    public record PreparationResult(boolean listOnly, String resolvedToken, Map<String, String> parameters) {
        @Override
        public String toString() {
            return "PreparationResult{listOnly=%s, resolvedToken=***, parameters=%s}"
                .formatted(listOnly, parameters != null ? parameters.keySet() : Map.of().keySet());
        }
    }

    private final AgentLoader agentLoader;
    private final SkillRegistrar skillRegistrar;
    private final TokenResolver tokenResolver;
    private final SkillLookup skillLookup;

    @Inject
    public SkillExecutionPreparation(AgentService agentService,
                                     SkillService skillService,
                                     GitHubTokenResolver tokenResolver) {
        this(
            additionalAgentDirs -> {
                List<Path> agentDirs = agentService.buildAgentDirectories(additionalAgentDirs);
                return agentService.loadAllAgents(agentDirs);
            },
            skillService::registerAllAgentSkills,
            tokenResolver::resolve,
            skillId -> skillService.getSkill(skillId).isPresent()
        );
    }

    SkillExecutionPreparation(AgentLoader agentLoader,
                              SkillRegistrar skillRegistrar,
                              TokenResolver tokenResolver,
                              SkillLookup skillLookup) {
        this.agentLoader = agentLoader;
        this.skillRegistrar = skillRegistrar;
        this.tokenResolver = tokenResolver;
        this.skillLookup = skillLookup;
    }

    public PreparationResult prepare(SkillCommand.ParsedOptions options) {
        Map<String, AgentConfig> agents = loadAgents(options.additionalAgentDirs());
        registerAgentSkills(agents);

        if (options.listSkills()) {
            return listOnlyResult();
        }

        return prepareExecution(options);
    }

    private PreparationResult prepareExecution(SkillCommand.ParsedOptions options) {
        String skillId = requireSkillId(options.skillId());
        String resolvedToken = resolveRequiredToken(options.githubToken());
        ensureSkillExists(skillId);
        Map<String, String> parameters = parseParameters(options.paramStrings());

        return executionResult(resolvedToken, parameters);
    }

    private PreparationResult listOnlyResult() {
        return new PreparationResult(true, null, Map.of());
    }

    private String requireSkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new CliValidationException("Skill ID required. Use --list to see available skills.", true);
        }
        return skillId;
    }

    private String resolveRequiredToken(String githubToken) {
        String resolvedToken = tokenResolver.resolve(githubToken).orElse(null);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new CliValidationException(
                "GitHub token is required. Use --token - (stdin) or login with `gh auth login` so `gh auth token` can be used.",
                true
            );
        }
        return resolvedToken;
    }

    private void ensureSkillExists(String skillId) {
        if (!skillLookup.exists(skillId)) {
            throw new CliValidationException("Skill not found: " + skillId, true);
        }
    }

    private PreparationResult executionResult(String resolvedToken, Map<String, String> parameters) {
        return new PreparationResult(false, resolvedToken, parameters);
    }

    private Map<String, AgentConfig> loadAgents(List<Path> additionalAgentDirs) {
        try {
            return agentLoader.load(additionalAgentDirs);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agents", e);
        }
    }

    private void registerAgentSkills(Map<String, AgentConfig> agents) {
        skillRegistrar.register(agents);
    }

    static Map<String, String> parseParameters(List<String> paramStrings) {
        Map<String, String> params = new HashMap<>();
        if (paramStrings != null) {
            for (String paramStr : paramStrings) {
                int eqIdx = paramStr.indexOf('=');
                if (eqIdx > 0) {
                    params.put(paramStr.substring(0, eqIdx).trim(), paramStr.substring(eqIdx + 1).trim());
                } else {
                    throw new CliValidationException(
                        "Invalid parameter format: '" + paramStr + "'. Expected 'key=value'.", true);
                }
            }
        }
        return Map.copyOf(params);
    }
}
