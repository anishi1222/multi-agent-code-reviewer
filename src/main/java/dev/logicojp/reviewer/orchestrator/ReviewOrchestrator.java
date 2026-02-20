package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.ReviewerConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import com.github.copilot.sdk.CopilotClient;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Orchestrates parallel code review execution using multiple AI agents.
///
/// Uses {@link StructuredTaskScope} for structured concurrency with
/// semaphore-based concurrency limiting. Each agent runs in a virtual thread
/// with timeout enforcement.
///
/// Merges 13 v1 orchestrator files into a single class:
/// ReviewOrchestrator, OrchestratorConfig, OrchestratorCollaborators,
/// ExecutorResources, ReviewExecutionModeRunner, AgentReviewExecutor,
/// ReviewContextFactory, ReviewResultPipeline, LocalSourcePrecomputer,
/// PromptTexts, AgentReviewer(Factory), LocalSourceCollector(Factory).
public class ReviewOrchestrator implements AutoCloseable {

    private static final int SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);

    private final CopilotClient client;
    private final ExecutionConfig executionConfig;
    private final List<CustomInstruction> customInstructions;
    private final ReviewerConfig.LocalFiles localFileConfig;
    private final ReviewAgent.PromptTemplates promptTemplates;
    private final Map<String, Object> cachedMcpServers;
    private final String reasoningEffort;
    private final String outputConstraints;
    private final ScheduledExecutorService sharedScheduler;
    private final Semaphore concurrencyLimit;
    private final Path checkpointRootDirectory;

    public ReviewOrchestrator(CopilotClient client, Config config) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.executionConfig = Objects.requireNonNull(config.executionConfig(), "executionConfig must not be null");
        this.customInstructions = config.customInstructions() != null
            ? List.copyOf(config.customInstructions()) : List.of();
        this.localFileConfig = config.localFileConfig() != null
            ? config.localFileConfig() : new ReviewerConfig.LocalFiles();
        this.promptTemplates = config.promptTemplates() != null
            ? config.promptTemplates() : new ReviewAgent.PromptTemplates(null, null, null);
        this.reasoningEffort = config.reasoningEffort();
        this.outputConstraints = config.outputConstraints();
        this.cachedMcpServers = GithubMcpConfig.buildMcpServers(
            config.githubToken(), config.githubMcpConfig()).orElse(Map.of());
        this.concurrencyLimit = new Semaphore(executionConfig.parallelism());
        this.checkpointRootDirectory = Path.of(executionConfig.checkpointDirectory()).normalize();
        this.sharedScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idle-timeout-shared");
            t.setDaemon(true);
            return t;
        });

        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (executionConfig.reviewPasses() > 1) {
            logger.info("Multi-pass review enabled: {} passes per agent", executionConfig.reviewPasses());
        }
        if (!this.customInstructions.isEmpty()) {
            logger.info("Custom instructions loaded ({} instruction(s))", this.customInstructions.size());
        }
    }

    // ========================================================================
    // Configuration record
    // ========================================================================

    /// Immutable configuration bundle for creating a ReviewOrchestrator.
    public record Config(
        @Nullable String githubToken,
        @Nullable GithubMcpConfig githubMcpConfig,
        ReviewerConfig.LocalFiles localFileConfig,
        ExecutionConfig executionConfig,
        List<CustomInstruction> customInstructions,
        @Nullable String reasoningEffort,
        @Nullable String outputConstraints,
        @Nullable ReviewAgent.PromptTemplates promptTemplates
    ) {
        public Config {
            executionConfig = Objects.requireNonNull(executionConfig, "executionConfig must not be null");
            localFileConfig = localFileConfig != null ? localFileConfig : new ReviewerConfig.LocalFiles();
            customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        }
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /// Executes reviews for all agents in parallel using StructuredTaskScope.
    public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, ReviewTarget target) {
        int reviewPasses = executionConfig.reviewPasses();
        int totalTasks = agents.size() * reviewPasses;
        logger.info("Starting parallel review for {} agents ({} passes each, {} total tasks) on target: {}",
            agents.size(), reviewPasses, totalTasks, target.displayName());

        String cachedSourceContent = preComputeSourceContent(target);
        ReviewContext sharedContext = createReviewContext(cachedSourceContent);
        return executeStructured(agents, target, sharedContext);
    }

    // ========================================================================
    // Structured concurrency execution
    // ========================================================================

    private List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                                 ReviewTarget target,
                                                 ReviewContext sharedContext) {
        int reviewPasses = executionConfig.reviewPasses();
        long perAgentTimeoutMinutes = perAgentTimeoutMinutes();
        long orchestratorTimeoutMinutes = executionConfig.orchestratorTimeoutMinutes();

        List<StructuredTaskScope.Subtask<List<ReviewResult>>> subtasks = new ArrayList<>(agents.size());
        List<AgentConfig> configs = new ArrayList<>(agents.size());

        try (var scope = StructuredTaskScope.<List<ReviewResult>>open()) {
            for (var config : agents.values()) {
                subtasks.add(scope.fork(() -> executeAgentPassesSafely(
                    config, target, sharedContext, reviewPasses, perAgentTimeoutMinutes)));
                configs.add(config);
            }

            joinStructuredWithTimeout(scope, orchestratorTimeoutMinutes);

            List<ReviewResult> results = new ArrayList<>();
            for (int i = 0; i < subtasks.size(); i++) {
                results.addAll(summarizeTaskResult(subtasks.get(i), configs.get(i), target, reviewPasses));
            }
            return finalizeResults(results, reviewPasses);
        }
    }

    private void joinStructuredWithTimeout(StructuredTaskScope<List<ReviewResult>, ?> scope,
                                           long timeoutMinutes) {
        try {
            StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Orchestrator join interrupted");
        } catch (TimeoutException e) {
            logger.warn("Orchestrator timed out after {} minutes", timeoutMinutes);
        }
    }

    private List<ReviewResult> summarizeTaskResult(StructuredTaskScope.Subtask<List<ReviewResult>> subtask,
                                                   AgentConfig config,
                                                   ReviewTarget target,
                                                   int reviewPasses) {
        List<ReviewResult> results = switch (subtask.state()) {
            case SUCCESS -> subtask.get();
            case FAILED -> {
                logger.error("Agent {} failed: {}", config.name(), subtask.exception().getMessage());
                yield ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                    "Review failed: " + subtask.exception().getMessage());
            }
            case UNAVAILABLE -> {
                logger.warn("Agent {} was unavailable (timed out)", config.name());
                yield ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                    "Review timed out or was cancelled");
            }
        };
        persistIntermediateResults(config, target, results);
        return results;
    }

    // ========================================================================
    // Agent execution with semaphore and timeout
    // ========================================================================

    private List<ReviewResult> executeAgentPassesSafely(AgentConfig config,
                                                        ReviewTarget target,
                                                        ReviewContext context,
                                                        int reviewPasses,
                                                        long perAgentTimeoutMinutes) {
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review interrupted while waiting for concurrency permit");
        }
        try {
            return executePassesWithTimeout(config, target, context, reviewPasses, perAgentTimeoutMinutes);
        } finally {
            concurrencyLimit.release();
        }
    }

    private List<ReviewResult> executePassesWithTimeout(AgentConfig config,
                                                        ReviewTarget target,
                                                        ReviewContext context,
                                                        int reviewPasses,
                                                        long perAgentTimeoutMinutes) {
        long totalTimeoutMinutes = perAgentTimeoutMinutes * Math.max(1, reviewPasses);
        try {
            logger.debug("Starting agent: {} (timeout: {} min)", config.name(), perAgentTimeoutMinutes);
            ReviewAgent agent = new ReviewAgent(config, context, promptTemplates);

            try (var innerScope = StructuredTaskScope.<List<ReviewResult>>open()) {
                var task = innerScope.fork(() -> agent.reviewPasses(target, reviewPasses));
                StructuredConcurrencyUtils.joinWithTimeout(innerScope, totalTimeoutMinutes, TimeUnit.MINUTES);

                return switch (task.state()) {
                    case SUCCESS -> task.get();
                    case FAILED -> throw asRuntimeException(task.exception());
                    case UNAVAILABLE -> ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                        "Review timed out after " + totalTimeoutMinutes + " minutes");
                };
            }
        } catch (TimeoutException e) {
            logger.warn("Agent {} timed out after {} minutes", config.name(), totalTimeoutMinutes, e);
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review timed out after " + totalTimeoutMinutes + " minutes");
        } catch (RuntimeException e) {
            logger.error("Agent {} execution failed: {}", config.name(), e.getMessage(), e);
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review failed: " + e.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return ReviewResult.failedResults(config, target.displayName(), reviewPasses,
                "Review interrupted during execution");
        }
    }

    private long perAgentTimeoutMinutes() {
        return executionConfig.agentTimeoutMinutes() * (executionConfig.maxRetries() + 1L);
    }

    // ========================================================================
    // ReviewContext factory
    // ========================================================================

    private ReviewContext createReviewContext(String cachedSourceContent) {
        return ReviewContext.builder()
            .client(client)
            .timeoutMinutes(executionConfig.agentTimeoutMinutes())
            .idleTimeoutMinutes(executionConfig.idleTimeoutMinutes())
            .customInstructions(customInstructions)
            .reasoningEffort(reasoningEffort)
            .maxRetries(executionConfig.maxRetries())
            .outputConstraints(outputConstraints)
            .cachedMcpServers(cachedMcpServers)
            .cachedSourceContent(cachedSourceContent)
            .localFileConfig(localFileConfig)
            .sharedScheduler(sharedScheduler)
            .agentTuningConfig(new ReviewContext.AgentTuningConfig(
                executionConfig.maxAccumulatedSize(),
                executionConfig.initialAccumulatedCapacity(),
                executionConfig.instructionBufferExtraCapacity()))
            .build();
    }

    // ========================================================================
    // Local source precomputation
    // ========================================================================

    private String preComputeSourceContent(ReviewTarget target) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> {
                logger.info("Pre-computing source content for local directory: {}", directory);
                var provider = new LocalFileProvider(directory, localFileConfig);
                var collection = provider.collectAndGenerate();
                logger.info("Collected {} source files from local directory", collection.fileCount());
                logger.debug("Directory summary:\n{}", collection.directorySummary());
                yield collection.reviewContent();
            }
            case ReviewTarget.GitHubTarget(_) -> null;
        };
    }

    // ========================================================================
    // Result pipeline
    // ========================================================================

    private List<ReviewResult> finalizeResults(List<ReviewResult> results, int reviewPasses) {
        List<ReviewResult> filtered = results.stream()
            .filter(Objects::nonNull).toList();

        long successCount = filtered.stream().filter(ReviewResult::success).count();
        logger.info("Completed {} reviews (success: {}, failed: {})",
            filtered.size(), successCount, filtered.size() - successCount);

        if (reviewPasses <= 1) return filtered;

        List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(filtered);
        logger.info("Merged {} pass results into {} agent results", filtered.size(), merged.size());
        return merged;
    }

    // ========================================================================
    // Resource cleanup
    // ========================================================================

    @Override
    public void close() {
        shutdownGracefully(sharedScheduler, SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
    }

    private static void shutdownGracefully(ExecutorService executor, int timeoutSeconds) {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static RuntimeException asRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable == null) {
            return new IllegalStateException("Unknown execution failure");
        }
        return new RuntimeException(throwable);
    }

    private void persistIntermediateResults(AgentConfig config,
                                            ReviewTarget target,
                                            List<ReviewResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(checkpointRootDirectory);
            String safeAgentName = config.name().replaceAll("[^a-zA-Z0-9._-]", "_");
            String safeTarget = target.displayName().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path checkpointPath = checkpointRootDirectory.resolve(safeTarget + "_" + safeAgentName + ".md");
            Path tempFile = Files.createTempFile(checkpointRootDirectory, ".checkpoint-", ".tmp");

            StringBuilder sb = new StringBuilder();
            sb.append("# Intermediate Review Checkpoint\n");
            sb.append("agent=").append(config.name()).append('\n');
            sb.append("target=").append(target.displayName()).append('\n');
            for (ReviewResult result : results) {
                sb.append("\n## pass-result\n");
                sb.append("success=").append(result.success()).append('\n');
                if (result.errorMessage() != null) {
                    sb.append("error=").append(result.errorMessage()).append('\n');
                }
                if (result.content() != null && !result.content().isBlank()) {
                    sb.append(result.content()).append('\n');
                }
            }

            Files.writeString(tempFile, sb.toString());
            Files.move(tempFile, checkpointPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            logger.debug("Persisted intermediate checkpoint for agent {}: {}", config.name(), checkpointPath);
        } catch (Exception e) {
            logger.warn("Failed to persist intermediate checkpoint for {}: {}", config.name(), e.getMessage());
        }
    }
}
