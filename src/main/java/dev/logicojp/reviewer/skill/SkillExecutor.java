package dev.logicojp.reviewer.skill;

import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import dev.logicojp.reviewer.util.ExecutorUtils;
import dev.logicojp.reviewer.util.RetryExecutor;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Executes skills using the Copilot SDK.
public class SkillExecutor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutor.class);
    private static final int MAX_RETRIES = 1;
    private static final long BACKOFF_BASE_MS = 500L;
    private static final long BACKOFF_MAX_MS = 15_000L;
    private final CopilotClient client;
    private final String defaultModel;
    private final long timeoutMinutes;
    private final int maxParameterValueLength;
    private final int executorShutdownTimeoutSeconds;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final boolean structuredConcurrencyEnabled;
    private final Map<String, Object> cachedMcpServers;
    private final SharedCircuitBreaker circuitBreaker;

    public SkillExecutor(CopilotClient client, String githubToken,
                         GithubMcpConfig githubMcpConfig,
                         SkillExecutorConfig config,
                         Executor executor,
                         boolean ownsExecutor) {
        this(
            client,
            githubToken,
            githubMcpConfig,
            config,
            executor,
            ownsExecutor,
            SharedCircuitBreaker.withDefaultConfig()
        );
    }

    public SkillExecutor(CopilotClient client,
                         String githubToken,
                         GithubMcpConfig githubMcpConfig,
                         SkillExecutorConfig config,
                         Executor executor,
                         boolean ownsExecutor,
                         SharedCircuitBreaker circuitBreaker) {
        this.client = client;
        this.defaultModel = config.defaultModel();
        this.timeoutMinutes = config.timeoutMinutes();
        this.maxParameterValueLength = config.maxParameterValueLength();
        this.executorShutdownTimeoutSeconds = config.executorShutdownTimeoutSeconds();
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        this.structuredConcurrencyEnabled = config.structuredConcurrencyEnabled();
        this.cachedMcpServers = GithubMcpConfig.buildMcpServers(githubToken, githubMcpConfig).orElse(Map.of());
        this.circuitBreaker = circuitBreaker;
    }

    /// Configuration values for {@link SkillExecutor} behavior.
    public record SkillExecutorConfig(
        String defaultModel,
        long timeoutMinutes,
        boolean structuredConcurrencyEnabled,
        int maxParameterValueLength,
        int executorShutdownTimeoutSeconds
    ) {
        public SkillExecutorConfig {
            defaultModel = Objects.requireNonNull(defaultModel, "defaultModel must not be null");
        }
    }

    /// Executes a skill with the given parameters.
    public CompletableFuture<SkillResult> execute(SkillDefinition skill, Map<String, String> parameters) {
        return execute(skill, parameters, null);
    }

    /// Executes a skill with the given parameters and system prompt.
    public CompletableFuture<SkillResult> execute(SkillDefinition skill,
                                                  Map<String, String> parameters,
                                                  @Nullable String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> executeSafely(skill, parameters, systemPrompt), executor);
    }

    private SkillResult executeSafely(SkillDefinition skill,
                                      Map<String, String> parameters,
                                      String systemPrompt) {
        RetryExecutor<SkillResult> retryExecutor = new RetryExecutor<>(
            MAX_RETRIES,
            BACKOFF_BASE_MS,
            BACKOFF_MAX_MS,
            Thread::sleep,
            circuitBreaker
        );

        return retryExecutor.execute(
            () -> structuredConcurrencyEnabled
                ? executeWithStructuredConcurrency(skill, parameters, systemPrompt)
                : executeSync(skill, parameters, systemPrompt),
            e -> SkillResult.failure(skill.id(), e.getMessage()),
            SkillResult::success,
            this::isRetryableFailure,
            this::isTransientException,
            skillRetryObserver(skill.id())
        );
    }

    private static RetryExecutor.RetryObserver<SkillResult> skillRetryObserver(String skillId) {
        return new RetryExecutor.RetryObserver<>() {
            @Override
            public void onCircuitOpen() {
                logger.warn("Skill {} skipped by open circuit breaker", skillId);
            }

            @Override
            public void onSuccess(int attempt, int totalAttempts, SkillResult result) {
                if (attempt > 1) {
                    logger.info("Skill {} succeeded on retry attempt {}/{}", skillId, attempt, totalAttempts);
                }
            }

            @Override
            public void onRetryableResult(int attempt, int totalAttempts, SkillResult result) {
                logger.warn("Skill {} failed on attempt {}/{}: {}. Retrying...",
                    skillId, attempt, totalAttempts, result.errorMessage());
            }

            @Override
            public void onFinalException(int attempt,
                                         int totalAttempts,
                                         Exception exception,
                                         boolean transientFailure) {
                logger.error("Skill execution failed for {}: {}", skillId, exception.getMessage(), exception);
            }

            @Override
            public void onRetryableException(int attempt, int totalAttempts, Exception exception) {
                logger.warn("Skill {} threw exception on attempt {}/{}: {}. Retrying...",
                    skillId, attempt, totalAttempts, exception.getMessage(), exception);
            }
        };
    }

    private boolean isRetryableFailure(SkillResult result) {
        return RetryPolicyUtils.isRetryableFailureMessage(
            result.errorMessage(),
            "missing required parameter",
            "validation"
        );
    }

    private boolean isTransientException(Exception exception) {
        return RetryPolicyUtils.isTransientException(exception);
    }

    private SkillResult executeWithStructuredConcurrency(SkillDefinition skill,
                                                         Map<String, String> parameters,
                                                         String systemPrompt) throws Exception {
        try (var scope = StructuredTaskScope.<SkillResult>open()) {
            var task = scope.fork(() -> executeSync(skill, parameters, systemPrompt));
            try {
                StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                scope.close();
                return SkillResult.failure(skill.id(),
                    "Skill timed out after " + timeoutMinutes + " minutes");
            }

            return switch (task.state()) {
                case SUCCESS -> task.get();
                case FAILED -> SkillResult.failure(skill.id(),
                    "Skill failed: " + (task.exception() != null ? task.exception().getMessage() : "unknown"));
                case UNAVAILABLE -> SkillResult.failure(skill.id(),
                    "Skill cancelled after " + timeoutMinutes + " minutes");
            };
        }
    }

    private SkillResult executeSync(SkillDefinition skill,
                                     Map<String, String> parameters,
                                     String systemPrompt) throws Exception {
        logger.info("Executing skill: {} with parameters: {}", skill.id(), parameters.keySet());

        skill.validateParameters(parameters);
        String prompt = skill.buildPrompt(parameters, maxParameterValueLength);
        SessionConfig sessionConfig = buildSkillSessionConfig(systemPrompt);
        long timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes);

        try (var session = client.createSession(sessionConfig).get(timeoutMinutes, TimeUnit.MINUTES)) {
            logger.debug("Sending skill prompt: {} (timeout: {} min)", skill.id(), timeoutMinutes);
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);

            String content = response.getData().content();
            if (content == null || content.isBlank()) {
                logger.warn("Skill {} returned empty content", skill.id());
                return SkillResult.failure(skill.id(), "Skill returned empty content");
            }

            logger.info("Skill execution completed: {} (content length: {} chars)", skill.id(), content.length());
            return SkillResult.success(skill.id(), content);
        }
    }

    private SessionConfig buildSkillSessionConfig(String systemPrompt) {
        var sessionConfig = new SessionConfig().setModel(defaultModel);
        if (!cachedMcpServers.isEmpty()) {
            sessionConfig.setMcpServers(cachedMcpServers);
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sessionConfig.setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.APPEND)
                .setContent(systemPrompt));
        }
        return sessionConfig;
    }

    /// Shuts down the internally-owned executor, if any.
    @Override
    public void close() {
        if (ownsExecutor && executor instanceof ExecutorService es) {
            ExecutorUtils.shutdownGracefully(es, executorShutdownTimeoutSeconds);
        }
    }
}
