package dev.logicojp.reviewer.application.skill;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.outbound.ManageSkillCatalogPort;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SessionRequest;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import dev.logicojp.reviewer.shared.RetryExecutor;
import dev.logicojp.reviewer.shared.RetryPolicyUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/// Application use-case: execute a named skill with given parameters.
///
/// Implements {@link ExecuteSkillPort}. Delegates session execution to
/// {@link RunCopilotSessionPort} and discovered-skill lookup to
/// {@link ManageSkillCatalogPort}.
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Application layer: imports only {@code application.port.*}, {@code domain.*},
/// {@code shared.*}, and {@code java.*} — no {@code infrastructure.*}.
public final class ExecuteSkillUseCase implements ExecuteSkillPort {

    private static final Logger logger = Logger.getLogger(ExecuteSkillUseCase.class.getName());
    private static final String DEFAULT_SKILL_MODEL = "claude-sonnet-4.5";
    private static final int MAX_RETRIES = 1;
    private static final long BACKOFF_BASE_MS = 500L;
    private static final long BACKOFF_MAX_MS = 30_000L;
    private static final SharedCircuitBreaker SKILL_CIRCUIT_BREAKER =
        SharedCircuitBreaker.forSkillDomain();

    private final RunCopilotSessionPort runCopilotSession;
    private final ManageSkillCatalogPort skillCatalog;
    private final String defaultModel;
    private final RetryExecutor<SkillResult> retryExecutor;

    /// Full constructor used by production wiring.
    ///
    /// @param runCopilotSession outbound port for SDK session execution
    /// @param skillCatalog      canonical catalog populated by infrastructure discovery
    /// @param defaultModel      default model for skill sessions
    public ExecuteSkillUseCase(RunCopilotSessionPort runCopilotSession,
                                ManageSkillCatalogPort skillCatalog,
                                String defaultModel) {
        this.runCopilotSession = Objects.requireNonNull(runCopilotSession);
        this.skillCatalog = Objects.requireNonNull(skillCatalog);
        this.defaultModel = defaultModel != null && !defaultModel.isBlank()
            ? defaultModel : DEFAULT_SKILL_MODEL;
        this.retryExecutor = new RetryExecutor<>(
            MAX_RETRIES,
            BACKOFF_BASE_MS,
            BACKOFF_MAX_MS,
            Thread::sleep,
            SKILL_CIRCUIT_BREAKER
        );
    }

    @Override
    public SkillResult execute(String skillId, Map<String, String> parameters) {
        Optional<SkillDefinition> maybeSkill = skillCatalog.findById(skillId);
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

        logger.info(() -> "ExecuteSkillUseCase: executing skill '%s'".formatted(skillId));
        return retryExecutor.execute(
            () -> executeAttempt(skillId, request),
            exception -> mapException(skillId, exception),
            SkillResult::success,
            this::isRetryableFailure,
            RetryPolicyUtils::isTransientException,
            retryObserver(skillId)
        );
    }

    @Override
    public List<SkillDefinition> listSkills() {
        return skillCatalog.listAll();
    }

    private SkillResult executeAttempt(String skillId, SessionRequest request) throws Exception {
        String response = runCopilotSession.runSession(request);
        if (response == null || response.isBlank()) {
            return SkillResult.failure(skillId, "Empty response from Copilot");
        }
        return SkillResult.success(skillId, response);
    }

    private SkillResult mapException(String skillId, Exception exception) {
        String detail = exception.getMessage() != null
            ? exception.getMessage()
            : exception.getClass().getSimpleName();
        if (hasCause(exception, TimeoutException.class)) {
            return SkillResult.failure(skillId, "Skill timed out: " + detail);
        }
        return SkillResult.failure(skillId, "Skill execution failed: " + detail);
    }

    private boolean isRetryableFailure(SkillResult result) {
        return RetryPolicyUtils.isRetryableFailureMessage(
            result.errorMessage(),
            "missing required parameter",
            "validation"
        );
    }

    private RetryExecutor.RetryObserver<SkillResult> retryObserver(String skillId) {
        return new RetryExecutor.RetryObserver<>() {
            @Override
            public void onCircuitOpen() {
                logger.warning(() -> "ExecuteSkillUseCase: skill '%s' skipped by open circuit breaker"
                    .formatted(skillId));
            }

            @Override
            public void onSuccess(int attempt, int totalAttempts, SkillResult result) {
                if (attempt > 1) {
                    logger.info(() -> "ExecuteSkillUseCase: skill '%s' succeeded on attempt %d/%d"
                        .formatted(skillId, attempt, totalAttempts));
                }
            }

            @Override
            public void onRetryableResult(int attempt, int totalAttempts, SkillResult result) {
                logger.warning(() -> "ExecuteSkillUseCase: skill '%s' failed on attempt %d/%d: %s. Retrying..."
                    .formatted(skillId, attempt, totalAttempts, result.errorMessage()));
            }

            @Override
            public void onFinalResultFailure(int attempt, int totalAttempts, SkillResult result,
                                             boolean retryable) {
                logger.warning(() -> "ExecuteSkillUseCase: skill '%s' failed on final attempt %d/%d: %s"
                    .formatted(skillId, attempt, totalAttempts, result.errorMessage()));
            }

            @Override
            public void onRetryableException(int attempt, int totalAttempts, Exception exception) {
                logger.warning(() -> "ExecuteSkillUseCase: skill '%s' threw on attempt %d/%d: %s. Retrying..."
                    .formatted(skillId, attempt, totalAttempts, exception.getMessage()));
            }

            @Override
            public void onFinalException(int attempt, int totalAttempts, Exception exception,
                                         boolean transientFailure) {
                logger.warning(() -> "ExecuteSkillUseCase: skill '%s' failed on final attempt %d/%d: %s"
                    .formatted(skillId, attempt, totalAttempts, exception.getMessage()));
            }
        };
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
