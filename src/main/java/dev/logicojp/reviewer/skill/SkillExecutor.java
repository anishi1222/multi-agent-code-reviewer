package dev.logicojp.reviewer.skill;

import dev.logicojp.reviewer.util.ApiCircuitBreaker;
import dev.logicojp.reviewer.util.BackoffUtils;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Executes skills using the Copilot SDK with StructuredTaskScope for timeout control.
public class SkillExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutor.class);
    /// Result of a skill execution.
    public record Result(
        String skillId,
        boolean success,
        String content,
        String errorMessage,
        Instant timestamp
    ) {
        public Result {
            timestamp = (timestamp == null) ? Instant.now() : timestamp;
        }

        public static Result success(String skillId, String content) {
            return new Result(skillId, true, content, null, Instant.now(Clock.systemUTC()));
        }

        public static Result failure(String skillId, String errorMessage) {
            return new Result(skillId, false, null, errorMessage, Instant.now(Clock.systemUTC()));
        }

        public static Result success(String skillId, String content, Clock clock) {
            return new Result(skillId, true, content, null, Instant.now(clock));
        }

        public static Result failure(String skillId, String errorMessage, Clock clock) {
            return new Result(skillId, false, null, errorMessage, Instant.now(clock));
        }
    }

    /// Configuration values for {@link SkillExecutor} behavior.
    public record Config(
        String defaultModel,
        long timeoutMinutes,
        int maxParameterValueLength,
        int executorShutdownTimeoutSeconds,
        int failureThreshold,
        long openDurationSeconds,
        int maxAttempts,
        long backoffBaseMs,
        long backoffMaxMs
    ) {
        public Config {
            defaultModel = Objects.requireNonNull(defaultModel, "defaultModel must not be null");
        }
    }

    private final CopilotClient client;
    private final String defaultModel;
    private final long timeoutMinutes;
    private final int maxParameterValueLength;
    private final Map<String, Object> cachedMcpServers;
    private final ApiCircuitBreaker apiCircuitBreaker;
    private final int maxAttempts;
    private final long backoffBaseMs;
    private final long backoffMaxMs;

    public SkillExecutor(CopilotClient client,
                         Map<String, Object> mcpServers,
                         Config config) {
        this.client = client;
        this.defaultModel = config.defaultModel();
        this.timeoutMinutes = config.timeoutMinutes();
        this.maxParameterValueLength = config.maxParameterValueLength();
        this.cachedMcpServers = (mcpServers == null || mcpServers.isEmpty())
            ? Map.of()
            : Map.copyOf(mcpServers);
        this.apiCircuitBreaker = new ApiCircuitBreaker(
            config.failureThreshold(),
            TimeUnit.SECONDS.toMillis(config.openDurationSeconds()),
            Clock.systemUTC());
        this.maxAttempts = Math.max(1, config.maxAttempts());
        this.backoffBaseMs = Math.max(1L, config.backoffBaseMs());
        this.backoffMaxMs = Math.max(this.backoffBaseMs, config.backoffMaxMs());
    }

    /// Executes a skill with the given parameters.
    public Result execute(SkillDefinition skill, Map<String, String> parameters) {
        return execute(skill, parameters, null);
    }

    /// Executes a skill with the given parameters and optional system prompt.
    public Result execute(SkillDefinition skill,
                          Map<String, String> parameters,
                          @Nullable String systemPrompt) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!apiCircuitBreaker.isRequestAllowed()) {
                return Result.failure(skill.id(),
                    "Copilot API circuit breaker is open (remaining "
                        + apiCircuitBreaker.remainingOpenMs() + " ms)");
            }
            try {
                Result result = executeWithTimeout(skill, parameters, systemPrompt);
                if (result.success()) {
                    apiCircuitBreaker.recordSuccess();
                    return result;
                }
                apiCircuitBreaker.recordFailure();
                if (attempt < maxAttempts && BackoffUtils.isRetryableMessage(result.errorMessage())) {
                    BackoffUtils.sleepWithJitterQuietly(attempt, backoffBaseMs, backoffMaxMs);
                    continue;
                }
                return result;
            } catch (Exception e) {
                apiCircuitBreaker.recordFailure();
                if (attempt < maxAttempts && BackoffUtils.isRetryableMessage(e.getMessage())) {
                    logger.warn("Skill {} attempt {}/{} failed: {}",
                        skill.id(), attempt, maxAttempts, e.getMessage());
                    BackoffUtils.sleepWithJitterQuietly(attempt, backoffBaseMs, backoffMaxMs);
                    continue;
                }
                logger.error("Skill execution failed for {}: {}", skill.id(), e.getMessage(), e);
                return Result.failure(skill.id(), e.getMessage());
            }
        }
        return Result.failure(skill.id(), "Skill execution exhausted retry attempts");
    }

    private Result executeWithTimeout(SkillDefinition skill,
                                      Map<String, String> parameters,
                                      String systemPrompt) throws Exception {
        try (var scope = StructuredTaskScope.<Result>open()) {
            var task = scope.fork(() -> executeSync(skill, parameters, systemPrompt));
            try {
                StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException _) {
                return Result.failure(skill.id(),
                    "Skill timed out after " + timeoutMinutes + " minutes");
            }

            return switch (task.state()) {
                case SUCCESS -> task.get();
                case FAILED -> Result.failure(skill.id(),
                    "Skill failed: " + (task.exception() != null ? task.exception().getMessage() : "unknown"));
                case UNAVAILABLE -> Result.failure(skill.id(),
                    "Skill cancelled after " + timeoutMinutes + " minutes");
            };
        }
    }

    private Result executeSync(SkillDefinition skill,
                               Map<String, String> parameters,
                               String systemPrompt) throws Exception {
        logger.info("Executing skill: {} with parameters: {}", skill.id(), parameters.keySet());

        skill.validateParameters(parameters);
        String prompt = skill.buildPrompt(parameters, maxParameterValueLength);

        var sessionConfigBuilder = new SessionConfig()
            .setModel(defaultModel);

        if (!cachedMcpServers.isEmpty()) {
            sessionConfigBuilder.setMcpServers(cachedMcpServers);
        }

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sessionConfigBuilder.setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));
        }

        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);

        try (var session = client.createSession(sessionConfigBuilder).get(Math.max(1, timeoutMinutes / 4), TimeUnit.MINUTES)) {
            logger.debug("Sending skill prompt: {} (timeout: {} min)", skill.id(), timeoutMinutes);
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);

            String content = response.getData().content();

            if (content == null || content.isBlank()) {
                logger.warn("Skill {} returned empty content", skill.id());
                return Result.failure(skill.id(), "Skill returned empty content");
            }

            logger.info("Skill execution completed: {} (content length: {} chars)", skill.id(), content.length());
            return Result.success(skill.id(), content);
        }
    }

}
