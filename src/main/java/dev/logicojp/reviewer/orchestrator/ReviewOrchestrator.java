package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.agent.SharedCircuitBreaker;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.ExecutorUtils;
import com.github.copilot.sdk.CopilotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/// Orchestrates parallel execution of multiple review agents.
///
/// **Lifecycle**: This class is intentionally NOT managed by Micronaut DI
/// because it owns per-invocation resources (`ExecutorService`, `ScheduledExecutorService`)
/// whose lifecycle must be bound to a single review execution.
/// Always use via try-with-resources:
/// ```java
/// try (var orchestrator = factory.create(...)) {
///     orchestrator.executeReviews(agents, target);
/// }
/// ```
///
/// Created by {@link ReviewOrchestratorFactory} which handles DI of shared services.
public class ReviewOrchestrator implements AutoCloseable {

    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60;
    private static final int SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);

    private final ExecutionConfig executionConfig;
    private final boolean structuredConcurrencyEnabled;
    /// Initialized in constructor — null when Structured Concurrency mode is active.
    private final ExecutorService executorService;
    /// Dedicated executor for per-agent review execution to avoid commonPool usage.
    private final ExecutorService agentExecutionExecutor;
    /// Shared scheduler for idle-timeout handling across all agents.
    private final ScheduledExecutorService sharedScheduler;
    private final ReviewExecutionModeRunner reviewExecutionModeRunner;
    private final AgentReviewExecutor agentReviewExecutor;
    private final ReviewContextFactory reviewContextFactory;
    private final LocalSourcePrecomputer localSourcePrecomputer;

    public ReviewOrchestrator(CopilotClient client, OrchestratorConfig orchestratorConfig) {
        this(client, orchestratorConfig, defaultCollaborators(
            client,
            orchestratorConfig,
            SharedCircuitBreaker.withDefaultConfig()
        ));
    }

    ReviewOrchestrator(CopilotClient client,
                       OrchestratorConfig orchestratorConfig,
                       AgentReviewerFactory reviewerFactory,
                       LocalSourceCollectorFactory localSourceCollectorFactory) {
        this(
            client,
            orchestratorConfig,
            collaboratorsFromFactories(
                client,
                orchestratorConfig,
                reviewerFactory,
                localSourceCollectorFactory,
                SharedCircuitBreaker.withDefaultConfig()
            )
        );
    }

    ReviewOrchestrator(CopilotClient client,
                       OrchestratorConfig orchestratorConfig,
                       OrchestratorCollaborators collaborators) {
        this.executionConfig = orchestratorConfig.executionConfig();
        this.structuredConcurrencyEnabled = orchestratorConfig.featureFlags().structuredConcurrency();
        var resources = collaborators.executorResources();
        this.executorService = resources.executorService();
        this.agentExecutionExecutor = resources.agentExecutionExecutor();
        this.sharedScheduler = resources.sharedScheduler();
        this.agentReviewExecutor = collaborators.agentReviewExecutor();
        this.reviewExecutionModeRunner = collaborators.reviewExecutionModeRunner();
        this.reviewContextFactory = collaborators.reviewContextFactory();
        this.localSourcePrecomputer = collaborators.localSourcePrecomputer();
        
        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (executionConfig.reviewPasses() > 1) {
            logger.info("Multi-pass review enabled: {} passes per agent", executionConfig.reviewPasses());
        }
    }

    static OrchestratorCollaborators defaultCollaborators(CopilotClient client,
                                                          OrchestratorConfig orchestratorConfig,
                                                          SharedCircuitBreaker reviewCircuitBreaker) {
        return collaboratorsFromFactories(
            client,
            orchestratorConfig,
            defaultReviewerFactory(orchestratorConfig),
            defaultLocalSourceCollectorFactory(),
            reviewCircuitBreaker
        );
    }

    private static OrchestratorCollaborators collaboratorsFromFactories(
            CopilotClient client,
            OrchestratorConfig orchestratorConfig,
            AgentReviewerFactory reviewerFactory,
            LocalSourceCollectorFactory localSourceCollectorFactory,
            SharedCircuitBreaker reviewCircuitBreaker) {
        boolean structuredConcurrencyEnabled = orchestratorConfig.featureFlags().structuredConcurrency();
        ExecutorService executorService = structuredConcurrencyEnabled
            ? null
            : Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("review-orchestrator-", 0).factory());
        ExecutorResources resources = null;
        try {
            resources = createExecutorResources(orchestratorConfig, executorService);
            return assembleCollaborators(client, orchestratorConfig, reviewerFactory,
                localSourceCollectorFactory, resources, reviewCircuitBreaker);
        } catch (Exception e) {
            if (resources != null) {
                resources.shutdownGracefully();
            } else {
                ExecutorUtils.shutdownGracefully(executorService, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
            }
            throw e;
        }
    }

    private static ExecutorResources createExecutorResources(
            OrchestratorConfig orchestratorConfig,
            ExecutorService executorService) {
        Semaphore concurrencyLimit = new Semaphore(orchestratorConfig.executionConfig().parallelism());
        ExecutorService agentExecutionExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("agent-execution-", 0).factory());
        // Scheduler uses one lightweight platform thread intentionally:
        // it only triggers periodic timeout checks and should not run blocking review work.
        ScheduledExecutorService sharedScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("idle-timeout-shared").factory()
        );
        return new ExecutorResources(executorService, agentExecutionExecutor,
            sharedScheduler, concurrencyLimit);
    }

    private static OrchestratorCollaborators assembleCollaborators(
            CopilotClient client,
            OrchestratorConfig orchestratorConfig,
            AgentReviewerFactory reviewerFactory,
            LocalSourceCollectorFactory localSourceCollectorFactory,
            ExecutorResources resources,
            SharedCircuitBreaker reviewCircuitBreaker) {
        Map<String, Object> cachedMcpServers = GithubMcpConfig.buildMcpServers(
            orchestratorConfig.githubToken(),
            orchestratorConfig.githubMcpConfig()
        ).orElse(Map.of());

        var executionPipeline = createExecutionPipeline(
            orchestratorConfig, resources, reviewerFactory);

        ReviewContextFactory reviewContextFactory = createReviewContextFactory(
            client, orchestratorConfig, cachedMcpServers, resources, reviewCircuitBreaker);

        LocalSourcePrecomputer localSourcePrecomputer = new LocalSourcePrecomputer(
            localSourceCollectorFactory, orchestratorConfig.localFileConfig());

        return new OrchestratorCollaborators(
            reviewerFactory, localSourceCollectorFactory, resources, cachedMcpServers,
            executionPipeline.pipeline(), executionPipeline.executor(),
            executionPipeline.modeRunner(), reviewContextFactory, localSourcePrecomputer);
    }

    private record ExecutionPipelineComponents(
        ReviewResultPipeline pipeline,
        AgentReviewExecutor executor,
        ReviewExecutionModeRunner modeRunner
    ) {}

    private static ExecutionPipelineComponents createExecutionPipeline(
            OrchestratorConfig orchestratorConfig,
            ExecutorResources resources,
            AgentReviewerFactory reviewerFactory) {
        ReviewResultPipeline pipeline = new ReviewResultPipeline();
        AgentReviewExecutor executor = new AgentReviewExecutor(
            resources.concurrencyLimit(), resources.agentExecutionExecutor(), reviewerFactory);
        ReviewExecutionModeRunner modeRunner = new ReviewExecutionModeRunner(
            orchestratorConfig.executionConfig(), resources.executorService(), pipeline);
        return new ExecutionPipelineComponents(pipeline, executor, modeRunner);
    }

    private static ReviewContextFactory createReviewContextFactory(
            CopilotClient client,
            OrchestratorConfig orchestratorConfig,
            Map<String, Object> cachedMcpServers,
            ExecutorResources resources,
            SharedCircuitBreaker reviewCircuitBreaker) {
        return new ReviewContextFactory(
            client, orchestratorConfig.executionConfig(),
            orchestratorConfig.reasoningEffort(),
            orchestratorConfig.outputConstraints(), cachedMcpServers,
            orchestratorConfig.localFileConfig(), resources.sharedScheduler(),
            reviewCircuitBreaker);
    }

    private static AgentReviewerFactory defaultReviewerFactory(OrchestratorConfig orchestratorConfig) {
        return (config, context) -> {
            var agent = new ReviewAgent(
                config,
                context,
                new ReviewAgent.PromptTemplates(
                    orchestratorConfig.promptTexts().focusAreasGuidance(),
                    orchestratorConfig.promptTexts().localSourceHeader(),
                    orchestratorConfig.promptTexts().localReviewResultRequest()
                )
            );
            return new AgentReviewer() {
                @Override
                public ReviewResult review(ReviewTarget target) {
                    return agent.review(target);
                }

                @Override
                public List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
                    return agent.reviewPasses(target, reviewPasses);
                }
            };
        };
    }

    private static LocalSourceCollectorFactory defaultLocalSourceCollectorFactory() {
        return (directory, config) -> {
            LocalFileProvider provider = new LocalFileProvider(directory, config);
            return provider::collectAndGenerate;
        };
    }
    
    /// Executes reviews for all provided agents in parallel.
    /// When `reviewPasses > 1`, each agent is reviewed multiple times in parallel
    /// and the results are merged per agent before returning.
    /// @param agents Map of agent name to AgentConfig
    /// @param target The target to review (GitHub repository or local directory)
    /// @return List of ReviewResults from all agents (one per agent, merged if multi-pass)
    public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, ReviewTarget target) {
        int reviewPasses = executionConfig.reviewPasses();
        int totalTasks = agents.size() * reviewPasses;
        logReviewStart(agents.size(), reviewPasses, totalTasks, target);

        var cachedSourceContent = localSourcePrecomputer.preComputeSourceContent(target);

        ReviewContext sharedContext = reviewContextFactory.create(cachedSourceContent);

        return executeByMode(agents, target, sharedContext);
    }

    private void logReviewStart(int agentCount,
                                int reviewPasses,
                                int totalTasks,
                                ReviewTarget target) {
        logger.info("Starting parallel review for {} agents ({} passes each, {} total tasks) on target: {}",
            agentCount, reviewPasses, totalTasks, target.displayName());
    }

    private List<ReviewResult> executeByMode(Map<String, AgentConfig> agents,
                                             ReviewTarget target,
                                             ReviewContext sharedContext) {
        if (structuredConcurrencyEnabled) {
            return reviewExecutionModeRunner.executeStructured(
                agents,
                target,
                sharedContext,
                agentReviewExecutor::executeAgentPassesSafely
            );
        }

        return reviewExecutionModeRunner.executeAsync(
            agents,
            target,
            sharedContext,
            agentReviewExecutor::executeAgentPassesSafely
        );
    }

    /// Closes executor resources.
    @Override
    public void close() {
        ExecutorUtils.shutdownGracefully(executorService, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        ExecutorUtils.shutdownGracefully(agentExecutionExecutor, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        ExecutorUtils.shutdownGracefully(sharedScheduler, SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
    }
}
