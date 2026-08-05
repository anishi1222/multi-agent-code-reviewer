package dev.logicojp.reviewer.application.skill;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/// Application use-case: execute a named skill with given parameters.
///
/// Implements {@link ExecuteSkillPort}. Delegates session execution to
/// {@link RunCopilotSessionPort} and skill lookup to a provided function
/// (injected by infrastructure to avoid an infrastructure import in the
/// application layer).
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code java.*} — no {@code infrastructure.*}.
public final class ExecuteSkillUseCase implements ExecuteSkillPort {

    private static final Logger logger = Logger.getLogger(ExecuteSkillUseCase.class.getName());
    private static final String DEFAULT_SKILL_MODEL = "claude-sonnet-4.5";

    private final RunCopilotSessionPort runCopilotSession;
    private final Function<String, Optional<SkillDefinition>> skillLookup;
    private final Supplier<List<SkillDefinition>> skillLister;
    private final String defaultModel;

    /// Full constructor used by production wiring.
    ///
    /// @param runCopilotSession outbound port for SDK session execution
    /// @param skillLookup       function mapping skillId → skill definition (from infrastructure)
    /// @param skillLister       supplier of all available skills (from infrastructure)
    /// @param defaultModel      default model for skill sessions
    public ExecuteSkillUseCase(RunCopilotSessionPort runCopilotSession,
                                Function<String, Optional<SkillDefinition>> skillLookup,
                                Supplier<List<SkillDefinition>> skillLister,
                                String defaultModel) {
        this.runCopilotSession = Objects.requireNonNull(runCopilotSession);
        this.skillLookup = Objects.requireNonNull(skillLookup);
        this.skillLister = Objects.requireNonNull(skillLister);
        this.defaultModel = defaultModel != null && !defaultModel.isBlank()
            ? defaultModel : DEFAULT_SKILL_MODEL;
    }

    /// Convenience constructor with empty skill registry for testing.
    public ExecuteSkillUseCase(RunCopilotSessionPort runCopilotSession) {
        this(runCopilotSession, id -> Optional.empty(), List::of, DEFAULT_SKILL_MODEL);
    }

    @Override
    public SkillResult execute(String skillId, Map<String, String> parameters) {
        Optional<SkillDefinition> maybeSkill = skillLookup.apply(skillId);
        if (maybeSkill.isEmpty()) {
            logger.warning(() -> "ExecuteSkillUseCase: skill '%s' not found".formatted(skillId));
            return SkillResult.failure(skillId, "Unknown skill: " + skillId);
        }
        SkillDefinition skill = maybeSkill.get();
        String resolvedPrompt = skill.buildPrompt(parameters != null ? parameters : Map.of(), 4096);
        if (resolvedPrompt == null || resolvedPrompt.isBlank()) {
            return SkillResult.failure(skillId, "Skill prompt is empty after parameter substitution");
        }

        AgentConfig agentConfig = AgentConfig.builder()
            .name("skill-" + skillId)
            .model(defaultModel)
            .build();
        var request = SessionRequest.of(agentConfig, resolvedPrompt);

        try {
            logger.info(() -> "ExecuteSkillUseCase: executing skill '%s'".formatted(skillId));
            String response = runCopilotSession.runSession(request);
            if (response == null || response.isBlank()) {
                return SkillResult.failure(skillId, "Empty response from Copilot");
            }
            return SkillResult.success(skillId, response);
        } catch (Exception e) {
            logger.warning(() -> "ExecuteSkillUseCase: skill '%s' failed: %s".formatted(skillId, e.getMessage()));
            return SkillResult.failure(skillId, "Skill execution failed: " + e.getMessage());
        }
    }

    @Override
    public List<SkillDefinition> listSkills() {
        return skillLister.get();
    }
}
