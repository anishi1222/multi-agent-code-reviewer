package dev.logicojp.reviewer.report.summary;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.util.CopilotPermissionHandlers;
import dev.logicojp.reviewer.util.RetryExecutor;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class AiSummaryClient {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryClient.class);
    private static final int AI_SUMMARY_MAX_RETRIES = 1;
    private static final long RETRY_BACKOFF_BASE_MS = 1_000L;
    private static final long RETRY_BACKOFF_MAX_MS = 15_000L;

    private final CopilotClient client;
    private final TemplateService templateService;
    private final String summaryModel;
    private final String reasoningEffort;
    private final long timeoutMinutes;
    private final SharedCircuitBreaker circuitBreaker;

    AiSummaryClient(CopilotClient client,
                    TemplateService templateService,
                    String summaryModel,
                    String reasoningEffort,
                    long timeoutMinutes,
                    SharedCircuitBreaker circuitBreaker) {
        this.client = client;
        this.templateService = Objects.requireNonNull(templateService);
        this.summaryModel = summaryModel;
        this.reasoningEffort = reasoningEffort;
        this.timeoutMinutes = timeoutMinutes;
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
    }

    String generate(String prompt) {
        logger.info("Using model for summary: {}", summaryModel);
        RetryExecutor<String> retryExecutor = new RetryExecutor<>(
            AI_SUMMARY_MAX_RETRIES,
            RETRY_BACKOFF_BASE_MS,
            RETRY_BACKOFF_MAX_MS,
            Thread::sleep,
            circuitBreaker
        );

        String summary = retryExecutor.execute(
            () -> runSummaryAttempt(prompt),
            e -> {
                logger.warn("Failed to create or execute summary session: {}", e.getMessage(), e);
                return null;
            },
            AiSummaryClient::isNonBlank,
            _ -> false,
            RetryPolicyUtils::isTransientException,
            summaryRetryObserver()
        );

        return isNonBlank(summary) ? summary : null;
    }

    private String runSummaryAttempt(String prompt)
            throws ExecutionException, TimeoutException {
        var sessionConfig = createSummarySessionConfig();
        long sessionCreateTimeoutMinutes = sessionCreateTimeoutMinutes(timeoutMinutes);

        try (CopilotSession session = client.createSession(sessionConfig)
            .get(sessionCreateTimeoutMinutes, TimeUnit.MINUTES)) {
            return sendUntilNonBlank(session, prompt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CopilotCliException("Summary generation interrupted", ex);
        }
    }

    private String sendUntilNonBlank(CopilotSession session, String prompt)
            throws ExecutionException, InterruptedException, TimeoutException {
        long timeoutMs = messageTimeoutMs(timeoutMinutes);
        int contentAttempts = AI_SUMMARY_MAX_RETRIES + 1;

        for (int attempt = 1; attempt <= contentAttempts; attempt++) {
            if (!circuitBreaker.allowRequest()) {
                logger.warn("Summary generation short-circuited by open circuit breaker on content attempt {}/{}",
                    attempt, contentAttempts);
                return null;
            }
            var response = session
                .sendAndWait(new MessageOptions().setPrompt(prompt), timeoutMs)
                .get(timeoutMinutes, TimeUnit.MINUTES);
            String content = response.getData().content();
            if (isNonBlank(content)) {
                return content;
            }
            if (attempt < contentAttempts) {
                RetryPolicyUtils.sleepWithBackoff(RETRY_BACKOFF_BASE_MS, RETRY_BACKOFF_MAX_MS, attempt);
                logger.warn(
                    "Summary generation returned empty content on attempt {}/{} in same session. Retrying...",
                    attempt, contentAttempts);
            }
        }
        return null;
    }

    static long sessionCreateTimeoutMinutes(long totalTimeoutMinutes) {
        return Math.max(1L, totalTimeoutMinutes / 4L);
    }

    static long messageTimeoutMs(long totalTimeoutMinutes) {
        return TimeUnit.MINUTES.toMillis(totalTimeoutMinutes);
    }

    SessionConfig createSummarySessionConfig() {
        String systemPrompt = templateService.getSummarySystemPrompt();
        var sessionConfig = new SessionConfig()
            .setModel(summaryModel)
            .setOnPermissionRequest(CopilotPermissionHandlers.DENY_ALL)
            .setSystemMessage(new SystemMessageConfig()
                .setMode(SystemMessageMode.REPLACE)
                .setContent(systemPrompt));

        applyReasoningEffort(sessionConfig);
        return sessionConfig;
    }

    private void applyReasoningEffort(SessionConfig sessionConfig) {
        String effort = ModelConfig.resolveReasoningEffort(summaryModel, reasoningEffort);
        if (effort != null) {
            logger.info("Setting reasoning effort '{}' for model: {}", effort, summaryModel);
            sessionConfig.setReasoningEffort(effort);
        }
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static RetryExecutor.RetryObserver<String> summaryRetryObserver() {
        return new RetryExecutor.RetryObserver<>() {
            @Override
            public void onCircuitOpen() {
                logger.warn("Summary generation skipped by open circuit breaker");
            }

            @Override
            public void onRetryableException(int attempt, int totalAttempts, Exception exception) {
                logger.warn("Summary generation failed on attempt {}/{}: {}. Retrying...",
                    attempt, totalAttempts, exception.getMessage(), exception);
            }

            @Override
            public void onFinalException(int attempt, int totalAttempts,
                                         Exception exception, boolean transientFailure) {
                if (!transientFailure) {
                    logger.warn("Summary generation failed without retry: {}", exception.getMessage(), exception);
                }
            }
        };
    }
}
