package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Prepares skill execution from presentation-layer parsed options.
///
/// Loads agents via {@link LoadAgentPort}, validates the skill via {@link ExecuteSkillPort},
/// and resolves the GitHub token via {@link ResolveTokenPort}.
@Singleton
public class SkillExecutionPreparation {

    public record PreparationResult(boolean listOnly, String resolvedToken, Map<String, String> parameters) {
        @Override
        public String toString() {
            return "PreparationResult{listOnly=%s, resolvedToken=***, parameters=%s}"
                .formatted(listOnly, parameters != null ? parameters.keySet() : Map.of().keySet());
        }
    }

    private final LoadAgentPort loadAgentPort;
    private final ExecuteSkillPort executeSkillPort;
    private final ResolveTokenPort tokenResolver;

    @Inject
    public SkillExecutionPreparation(
            LoadAgentPort loadAgentPort,
            ExecuteSkillPort executeSkillPort,
            ResolveTokenPort tokenResolver) {
        this.loadAgentPort = loadAgentPort;
        this.executeSkillPort = executeSkillPort;
        this.tokenResolver = tokenResolver;
    }

    public PreparationResult prepare(SkillOptions options) {
        // Side-effect: loads agents so the skill registry is populated
        // `--agents-dir` values come from argv only — user-supplied by definition (ADR-0007 D1).
        loadAgentPort.loadAll(AgentSourceDirectory.allUserSupplied(options.additionalAgentDirs()));

        if (options.listSkills()) {
            return new PreparationResult(true, null, Map.of());
        }

        return prepareExecution(options);
    }

    private PreparationResult prepareExecution(SkillOptions options) {
        String skillId = requireSkillId(options.skillId());
        String resolvedToken = resolveRequiredToken(options.githubToken());
        ensureSkillExists(skillId);
        Map<String, String> parameters = parseParameters(options.paramStrings());
        return new PreparationResult(false, resolvedToken, parameters);
    }

    private String requireSkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new CliValidationException(
                "Skill ID required. Use --list to see available skills.", true);
        }
        return skillId;
    }

    private String resolveRequiredToken(String githubToken) {
        String resolvedToken = tokenResolver.resolve(githubToken).orElse(null);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new CliValidationException(
                "GitHub token is required. Use --token - (stdin).",
                true
            );
        }
        return resolvedToken;
    }

    private void ensureSkillExists(String skillId) {
        List<SkillDefinition> available = executeSkillPort.listSkills();
        boolean found = available.stream().anyMatch(s -> s.id().equals(skillId));
        if (!found) {
            throw new CliValidationException("Skill not found: " + skillId, true);
        }
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
