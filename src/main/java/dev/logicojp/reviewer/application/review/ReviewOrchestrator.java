package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.application.port.outbound.CollectLocalSourcePort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.RunRubberDuckSessionPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;
import dev.logicojp.reviewer.domain.review.ReviewContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/// Top-level review orchestrator — implements the inbound port {@link RunReviewPort}.
///
/// Purified from {@code orchestrator.ReviewOrchestrator}:
/// - Removed {@code CopilotClient} constructor injection — lifecycle is now managed
///   through {@link ManageCopilotClientPort}.
/// - Removed {@code AutoCloseable} — the lifecycle of executor resources is scoped to
///   each {@link #execute(ReviewRequest)} invocation.
/// - Removed all DI annotations ({@code @Singleton}, {@code @Inject}).
/// - Replaced {@code AgentReviewerFactory} / {@code ReviewContextFactory} with
///   {@link ReviewPassRunner} / {@link RubberDuckDialogueRunner} + in-line context building.
/// - All imports restricted to {@code application.port.*}, {@code domain.*},
///   {@code shared.*}, and {@code java.*}.
public final class ReviewOrchestrator implements RunReviewPort {

    private static final Logger logger = Logger.getLogger(ReviewOrchestrator.class.getName());
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60;

    private final ManageCopilotClientPort manageCopilotClient;
    private final CollectLocalSourcePort collectLocalSource;
    private final LoadTemplatePort loadTemplate;
    private final RunCopilotSessionPort runCopilotSession;
    private final RunRubberDuckSessionPort runRubberDuckSession;
    private final OrchestratorConfig config;

    public ReviewOrchestrator(ManageCopilotClientPort manageCopilotClient,
                               CollectLocalSourcePort collectLocalSource,
                               LoadTemplatePort loadTemplate,
                               RunCopilotSessionPort runCopilotSession,
                               RunRubberDuckSessionPort runRubberDuckSession,
                               OrchestratorConfig config) {
        this.manageCopilotClient = manageCopilotClient;
        this.collectLocalSource = collectLocalSource;
        this.loadTemplate = loadTemplate;
        this.runCopilotSession = runCopilotSession;
        this.runRubberDuckSession = runRubberDuckSession;
        this.config = config;
    }

    /// Execute a full code review for the given request.
    ///
    /// <ol>
    ///   <li>Starts the Copilot client via {@link ManageCopilotClientPort}.</li>
    ///   <li>Pre-computes local source content (if the target is a local directory).</li>
    ///   <li>Builds a pure-domain {@link ReviewContext}.</li>
    ///   <li>Runs all agents in parallel (standard or rubber-duck mode).</li>
    ///   <li>Aggregates per-agent results into a single consolidated result.</li>
    ///   <li>Stops the Copilot client.</li>
    /// </ol>
    @Override
    public ReviewResult execute(ReviewRequest request) {
        logger.info(() -> "Starting review for: " + request.target().displayName()
            + " with " + request.agents().size() + " agent(s), parallelism=" + request.parallelism());

        manageCopilotClient.start(config.githubToken());
        try {
            return runReview(request);
        } finally {
            manageCopilotClient.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Internal orchestration
    // -------------------------------------------------------------------------

    private ReviewResult runReview(ReviewRequest request) {
        var precomputer = new LocalSourcePrecomputer(collectLocalSource);
        var cachedSource = precomputer
            .preComputeSourceContent(request.target(), request.localFileConfig())
            .orElse(null);

        var context = buildReviewContext(cachedSource);
        var agents = toAgentMap(request.agents());

        List<ReviewResult> allResults;
        if (isRubberDuckMode(request)) {
            allResults = executeRubberDuckReviews(agents, request, context);
        } else {
            allResults = executeStandardReviews(agents, request, context);
        }

        return aggregateResults(allResults, request.target().displayName());
    }

    private List<ReviewResult> executeStandardReviews(Map<String, AgentConfig> agents,
                                                       ReviewRequest request,
                                                       ReviewContext context) {
        logger.info(() -> "Running standard review: " + agents.size() + " agent(s)");
        var resources = buildExecutorResources(request.parallelism());
        var metrics = new OrchestratorMetrics();
        var pipeline = new ReviewResultPipeline();
        var factory = new ReviewResultFactory();
        var passRunner = new ReviewPassRunner(runCopilotSession, factory);
        var rubberDuckRunner = new RubberDuckDialogueRunner(runRubberDuckSession, loadTemplate, factory);
        var agentExecutor = new AgentReviewExecutor(
            resources.concurrencyLimit(),
            resources.agentExecutionExecutor(),
            passRunner,
            rubberDuckRunner,
            metrics);
        var modeRunner = new ReviewExecutionModeRunner(config, pipeline, metrics);

        try {
            return modeRunner.executeStructured(
                agents,
                request.target(),
                context,
                config.reviewPasses(),
                config.orchestratorTimeoutMinutes(),
                (agentConfig, target, ctx, passes, timeoutMinutes) ->
                    agentExecutor.executeAgentPassesSafely(
                        agentConfig, target, ctx, passes, timeoutMinutes,
                        List.<McpServerSpec>of(), ctx.maxRetries()));
        } finally {
            resources.shutdownGracefully();
        }
    }

    private List<ReviewResult> executeRubberDuckReviews(Map<String, AgentConfig> agents,
                                                          ReviewRequest request,
                                                          ReviewContext context) {
        logger.info(() -> "Running rubber-duck review: " + agents.size() + " agent(s)");
        var resources = buildExecutorResources(request.parallelism());
        var metrics = new OrchestratorMetrics();
        var pipeline = new ReviewResultPipeline();
        var factory = new ReviewResultFactory();
        var passRunner = new ReviewPassRunner(runCopilotSession, factory);
        var rubberDuckRunner = new RubberDuckDialogueRunner(runRubberDuckSession, loadTemplate, factory);
        var agentExecutor = new AgentReviewExecutor(
            resources.concurrencyLimit(),
            resources.agentExecutionExecutor(),
            passRunner,
            rubberDuckRunner,
            metrics);
        var modeRunner = new ReviewExecutionModeRunner(config, pipeline, metrics);

        int rounds = config.rubberDuckRounds();
        long perAgentTimeout = config.agentTimeoutMinutes() * (config.maxRetries() + 1L);

        try {
            return modeRunner.executeStructured(
                agents,
                request.target(),
                context,
                1,
                config.orchestratorTimeoutMinutes(),
                (agentConfig, target, ctx, passes, timeoutMinutes) ->
                    agentExecutor.executeRubberDuckSafely(
                        agentConfig, target, ctx, rounds, perAgentTimeout, List.<McpServerSpec>of()));
        } finally {
            resources.shutdownGracefully();
        }
    }

    // -------------------------------------------------------------------------
    // Context and helpers
    // -------------------------------------------------------------------------

    private ReviewContext buildReviewContext(String cachedSourceContent) {
        return ReviewContext.builder()
            .invocationTimestamp(config.invocationTimestamp())
            .reasoningEffort(config.reasoningEffort())
            .outputConstraints(config.outputConstraints())
            .cachedSourceContent(cachedSourceContent)
            .sharedSessionEnabled(config.sharedSessionEnabled())
            .maxRetries(config.maxRetries())
            .reviewCircuitBreaker(SharedCircuitBreaker.forReviewDomain())
            .build();
    }

    private boolean isRubberDuckMode(ReviewRequest request) {
        return request.rubberDuck() || config.rubberDuckEnabled();
    }

    private Map<String, AgentConfig> toAgentMap(List<AgentConfig> agents) {
        Map<String, AgentConfig> map = new LinkedHashMap<>();
        for (AgentConfig agent : agents) {
            map.put(agent.name(), agent);
        }
        return map;
    }

    private ExecutorResources buildExecutorResources(int parallelism) {
        var executor = Executors.newFixedThreadPool(parallelism,
            r -> Thread.ofPlatform().name("review-agent-", 0).unstarted(r));
        var semaphore = new Semaphore(parallelism);
        return new ExecutorResources(executor, semaphore);
    }

    private ReviewResult aggregateResults(List<ReviewResult> results, String targetDisplayName) {
        if (results.isEmpty()) {
            return ReviewResult.builder()
                .repository(targetDisplayName)
                .success(false)
                .errorMessage("No review results were produced")
                .build();
        }

        boolean anySuccess = results.stream().anyMatch(ReviewResult::success);
        String aggregatedContent = results.stream()
            .filter(r -> r.content() != null && !r.content().isBlank())
            .map(r -> {
                String agentName = r.agentConfig() != null ? r.agentConfig().name() : "unknown";
                return "### Review by: " + agentName + "\n\n" + r.content();
            })
            .collect(Collectors.joining("\n\n---\n\n"));

        String errorSummary = anySuccess ? null : results.stream()
            .filter(r -> !r.success() && r.errorMessage() != null)
            .map(ReviewResult::errorMessage)
            .collect(Collectors.joining("; "));

        var firstAgent = results.stream()
            .map(ReviewResult::agentConfig)
            .filter(a -> a != null)
            .findFirst()
            .orElse(null);

        return ReviewResult.builder()
            .agentConfig(firstAgent)
            .repository(targetDisplayName)
            .content(aggregatedContent.isBlank() ? null : aggregatedContent)
            .success(anySuccess)
            .errorMessage(errorSummary)
            .build();
    }
}
