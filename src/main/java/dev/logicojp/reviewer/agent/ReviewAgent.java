package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.sanitize.ContentSanitizer;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.events.AssistantMessageEvent;
import com.github.copilot.sdk.events.SessionErrorEvent;
import com.github.copilot.sdk.events.SessionIdleEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

/// Executes a code review using the Copilot SDK with a specific agent configuration.
public class ReviewAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewAgent.class);

    /// Follow-up prompt sent when the primary send returns empty content.
    /// Used as an in-session retry — much faster than a full retry since MCP context is already loaded.
    private static final String FOLLOWUP_PROMPT =
        "Please provide the complete review results in the specified output format.";

    record AgentCollaborators(
        ReviewTargetInstructionResolver reviewTargetInstructionResolver,
        ReviewSessionMessageSender reviewSessionMessageSender,
        ReviewRetryExecutor reviewRetryExecutor,
        ReviewSessionConfigFactory reviewSessionConfigFactory,
        ReviewResultFactory reviewResultFactory
    ) {
        AgentCollaborators {
            Objects.requireNonNull(reviewTargetInstructionResolver);
            Objects.requireNonNull(reviewSessionMessageSender);
            Objects.requireNonNull(reviewRetryExecutor);
            Objects.requireNonNull(reviewSessionConfigFactory);
            Objects.requireNonNull(reviewResultFactory);
        }
    }

    @FunctionalInterface
    interface AgentCollaboratorsFactory {
        AgentCollaborators create(AgentConfig config, ReviewContext ctx);
    }

    private static final AgentCollaboratorsFactory DEFAULT_COLLABORATORS_FACTORY = ReviewAgent::defaultCollaborators;

    public record PromptTemplates(
        String focusAreasGuidance,
        String localSourceHeader,
        String localReviewResultPrompt
    ) {
        public PromptTemplates {
            focusAreasGuidance = focusAreasGuidance != null ? focusAreasGuidance : "";
            localSourceHeader = localSourceHeader != null ? localSourceHeader : "";
            localReviewResultPrompt = localReviewResultPrompt != null ? localReviewResultPrompt : "";
        }

        static final PromptTemplates DEFAULTS = new PromptTemplates(
            AgentPromptBuilder.DEFAULT_FOCUS_AREAS_GUIDANCE,
            AgentPromptBuilder.DEFAULT_LOCAL_SOURCE_HEADER,
            AgentPromptBuilder.DEFAULT_LOCAL_REVIEW_RESULT_PROMPT
        );
    }
    
    private final AgentConfig config;
    private final ReviewContext ctx;
    private final IdleTimeoutScheduler idleTimeoutScheduler;
    private final ReviewSystemPromptFormatter reviewSystemPromptFormatter;
    private final ReviewTargetInstructionResolver reviewTargetInstructionResolver;
    private final ReviewSessionMessageSender reviewSessionMessageSender;
    private final ReviewRetryExecutor reviewRetryExecutor;
    private final ReviewSessionConfigFactory reviewSessionConfigFactory;
    private final ReviewResultFactory reviewResultFactory;
    private final String focusAreasGuidance;
    private final String localSourceHeaderPrompt;
    private final String localReviewResultPrompt;

    /// Creates default collaborators for a given agent configuration and context.
    /// Collaborators are created via `new` rather than DI because they are per-invocation
    /// objects whose lifecycle is bound to a single review execution — not shared singletons.
    /// For testing, use the full-parameter constructor to inject custom collaborators.
    static AgentCollaborators defaultCollaborators(AgentConfig config, ReviewContext ctx) {
        var tuning = ctx.agentTuningConfig();
        return new AgentCollaborators(
            new ReviewTargetInstructionResolver(
                config,
                ctx.localFileConfig(),
                () -> logger.debug("Computed source content locally for agent: {}", config.name())
            ),
            new ReviewSessionMessageSender(config.name(),
                tuning.maxAccumulatedSize(), tuning.initialAccumulatedCapacity()),
            new ReviewRetryExecutor(
                config.name(),
                ctx.timeoutConfig().maxRetries(),
                ReviewRetryExecutor.DEFAULT_BACKOFF_BASE_MS,
                ReviewRetryExecutor.DEFAULT_BACKOFF_MAX_MS,
                Thread::sleep,
                ctx.reviewCircuitBreaker()
            ),
            new ReviewSessionConfigFactory(),
            new ReviewResultFactory()
        );
    }

    public ReviewAgent(AgentConfig config, ReviewContext ctx) {
        this(config, ctx, PromptTemplates.DEFAULTS);
    }

    public ReviewAgent(AgentConfig config, ReviewContext ctx, PromptTemplates promptTemplates) {
        this(
            config,
            ctx,
            IdleTimeoutScheduler.defaultScheduler(),
            new ReviewSystemPromptFormatter(),
            promptTemplates.focusAreasGuidance(),
            promptTemplates.localSourceHeader(),
            promptTemplates.localReviewResultPrompt(),
            DEFAULT_COLLABORATORS_FACTORY.create(config, ctx)
        );
    }

    /// Full-parameter constructor for testing — all collaborators are injectable.
    ReviewAgent(AgentConfig config,
                ReviewContext ctx,
                IdleTimeoutScheduler idleTimeoutScheduler,
                ReviewSystemPromptFormatter reviewSystemPromptFormatter,
                String focusAreasGuidance,
                String localSourceHeaderPrompt,
                String localReviewResultPrompt,
                AgentCollaborators collaborators) {
        this.config = config;
        this.ctx = ctx;
        this.idleTimeoutScheduler = idleTimeoutScheduler;
        this.reviewSystemPromptFormatter = reviewSystemPromptFormatter;
        this.focusAreasGuidance = focusAreasGuidance;
        this.localSourceHeaderPrompt = localSourceHeaderPrompt;
        this.localReviewResultPrompt = localReviewResultPrompt;
        this.reviewTargetInstructionResolver = collaborators.reviewTargetInstructionResolver();
        this.reviewSessionMessageSender = collaborators.reviewSessionMessageSender();
        this.reviewRetryExecutor = collaborators.reviewRetryExecutor();
        this.reviewSessionConfigFactory = collaborators.reviewSessionConfigFactory();
        this.reviewResultFactory = collaborators.reviewResultFactory();
    }
    
    /// Executes the review synchronously on the calling thread with retry support.
    /// Each attempt gets the full configured timeout — the timeout is per-attempt, not cumulative.
    /// @param target The target to review (GitHub repository or local directory)
    /// @return ReviewResult containing the review content
    public ReviewResult review(ReviewTarget target) {
        return reviewForPass(target, 1, 1);
    }

    private ReviewResult reviewForPass(ReviewTarget target, int currentPass, int totalPasses) {
        return reviewRetryExecutor.execute(
            () -> executeReview(target, currentPass, totalPasses),
            e -> reviewResultFactory.fromException(config, target.displayName(), e)
        );
    }

    /// Executes multiple review passes while reusing a single Copilot session for this agent.
    /// This reduces MCP initialization overhead across passes.
    public List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
        if (reviewPasses <= 1) {
            return List.of(reviewForPass(target, 1, 1));
        }

        if (!ctx.sharedSessionEnabled()) {
            logger.info("Agent {}: shared session mode disabled, using isolated sessions for {} passes",
                config.name(), reviewPasses);
            return executeReviewPassesFallback(target, reviewPasses);
        }

        logger.info("Agent {}: reusing one Copilot session for {} passes", config.name(), reviewPasses);

        try {
            return executeReviewPasses(target, reviewPasses);
        } catch (Exception e) {
            logger.warn("Agent {}: shared session failed, falling back to individual sessions: {}",
                config.name(), e.getMessage(), e);
            return executeReviewPassesFallback(target, reviewPasses);
        }
    }

    private List<ReviewResult> executeReviewPassesFallback(ReviewTarget target, int reviewPasses) {
        try (var scope = StructuredTaskScope.<ReviewResult>open()) {
            List<StructuredTaskScope.Subtask<ReviewResult>> tasks = new ArrayList<>(reviewPasses);
            for (int pass = 1; pass <= reviewPasses; pass++) {
                int passNumber = pass;
                tasks.add(scope.fork(() -> reviewForPass(target, passNumber, reviewPasses)));
            }

            scope.join();
            return collectFallbackResults(tasks, target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of(reviewResultFactory.fromException(config, target.displayName(), e));
        }
    }

    private List<ReviewResult> collectFallbackResults(List<StructuredTaskScope.Subtask<ReviewResult>> subtasks,
                                                       ReviewTarget target)
            throws InterruptedException {
        List<ReviewResult> results = new ArrayList<>(subtasks.size());
        for (var subtask : subtasks) {
            switch (subtask.state()) {
                case SUCCESS -> results.add(subtask.get());
                case FAILED -> {
                    Throwable failure = subtask.exception();
                    Exception cause = failure instanceof Exception exception
                        ? exception
                        : new IllegalStateException("Fallback pass failed", failure);
                    logger.warn("Agent {}: fallback pass failed: {}", config.name(), cause.getMessage(), cause);
                    results.add(reviewResultFactory.fromException(config, target.displayName(), cause));
                }
                case UNAVAILABLE -> results.add(reviewResultFactory.fromException(
                    config,
                    target.displayName(),
                    new IllegalStateException("Fallback pass cancelled")
                ));
            }
        }
        return results;
    }
    
    private ReviewResult executeReview(ReviewTarget target, int currentPass, int totalPasses) throws Exception {
        logger.info("Starting review with agent: {} for target: {}", 
            config.name(), target.displayName());

        var resolvedInstruction = resolveTargetInstruction(target);

        return executeReviewCommon(
            target.displayName(),
            resolvedInstruction.instruction(),
            resolvedInstruction.localSourceContent(),
            resolvedInstruction.mcpServers(),
            currentPass,
            totalPasses
        );
    }

    private List<ReviewResult> executeReviewPasses(ReviewTarget target, int reviewPasses) throws Exception {
        if (reviewPasses <= 2) {
            return executeReviewPassesSequential(target, reviewPasses);
        }
        return executeReviewPassesHybrid(target, reviewPasses);
    }

    private List<ReviewResult> executeReviewPassesSequential(ReviewTarget target, int reviewPasses) throws Exception {
        logger.info("Starting {} review passes with shared session for agent: {} on target: {}",
            reviewPasses, config.name(), target.displayName());

        ResolvedReviewParams params = resolveReviewParams(target);
        String displayName = params.displayName();
        String instruction = params.instruction();
        String localSourceContent = params.localSourceContent();
        Map<String, Object> mcpServers = params.mcpServers();

        String systemPrompt = buildSystemPrompt();
        SessionConfig sessionConfig = reviewSessionConfigFactory.create(
            config,
            ctx,
            systemPrompt,
            mcpServers,
            1,
            reviewPasses
        );

        try (var session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutConfig().timeoutMinutes(), TimeUnit.MINUTES)) {
            List<ReviewResult> results = new ArrayList<>(reviewPasses);
            for (int pass = 1; pass <= reviewPasses; pass++) {
                int passNumber = pass;
                String localSourceContentForPass = resolveLocalSourceContentForPass(
                    target,
                    localSourceContent,
                    passNumber
                );
                logger.debug("Agent {}: executing pass {}/{} on shared session",
                    config.name(), passNumber, reviewPasses);
                ReviewResult result = reviewRetryExecutor.execute(
                    () -> executeReviewWithSession(
                        displayName,
                        instruction,
                        localSourceContentForPass,
                        mcpServers,
                        session
                    ),
                    e -> reviewResultFactory.fromException(config, displayName, e)
                );
                results.add(result);
            }
            return results;
        }
    }

    private List<ReviewResult> executeReviewPassesHybrid(ReviewTarget target, int reviewPasses) throws Exception {
        logger.info("Starting {} review passes with hybrid mode for agent: {} on target: {}",
            reviewPasses, config.name(), target.displayName());

        ResolvedReviewParams params = resolveReviewParams(target);

        List<ReviewResult> results = new ArrayList<>(reviewPasses);

        // First pass uses a single-session execution to amortize setup cost.
        String localSourceForFirstPass = resolveLocalSourceContentForPass(target, params.localSourceContent(), 1);
        results.add(reviewRetryExecutor.execute(
            () -> executeReviewCommon(
                params.displayName(),
                params.instruction(),
                localSourceForFirstPass,
                params.mcpServers(),
                1,
                reviewPasses
            ),
            e -> reviewResultFactory.fromException(config, params.displayName(), e)
        ));

        if (reviewPasses > 1) {
            results.addAll(submitAndCollectParallelPasses(target, params, reviewPasses));
        }

        return results;
    }

    /// Submits remaining passes (2..N) in parallel and collects results in pass order.
    private List<ReviewResult> submitAndCollectParallelPasses(
            ReviewTarget target, ResolvedReviewParams params, int reviewPasses)
            throws InterruptedException {
        try (var scope = StructuredTaskScope.<PassResult>open()) {
            List<StructuredTaskScope.Subtask<PassResult>> tasks = new ArrayList<>(reviewPasses - 1);
            for (int pass = 2; pass <= reviewPasses; pass++) {
                int passNumber = pass;
                tasks.add(scope.fork(() -> executeParallelPass(target, params, passNumber, reviewPasses)));
            }

            scope.join();
            return collectParallelPassResults(tasks);
        }
    }

    private PassResult executeParallelPass(ReviewTarget target,
                                           ResolvedReviewParams params,
                                           int passNumber,
                                           int totalPasses) {
        String localSourceForPass = resolveLocalSourceContentForPass(target, params.localSourceContent(), passNumber);
        ReviewResult result = reviewRetryExecutor.execute(
            () -> executeReviewCommon(
                params.displayName(),
                params.instruction(),
                localSourceForPass,
                params.mcpServers(),
                passNumber,
                totalPasses
            ),
            e -> reviewResultFactory.fromException(config, params.displayName(), e)
        );
        return new PassResult(passNumber, result);
    }

    private static List<ReviewResult> collectParallelPassResults(
            List<StructuredTaskScope.Subtask<PassResult>> subtasks) {
        List<PassResult> passResults = new ArrayList<>(subtasks.size());
        for (var subtask : subtasks) {
            switch (subtask.state()) {
                case SUCCESS -> passResults.add(subtask.get());
                case FAILED -> throw new IllegalStateException(
                    "Hybrid review pass execution failed",
                    subtask.exception()
                );
                case UNAVAILABLE -> throw new IllegalStateException("Hybrid review pass execution cancelled");
            }
        }
        passResults.sort(Comparator.comparingInt(PassResult::passNumber));
        return passResults.stream().map(PassResult::result).toList();
    }

    private record PassResult(int passNumber, ReviewResult result) {
    }

    private record ResolvedReviewParams(String displayName,
                                        String instruction,
                                        String localSourceContent,
                                        Map<String, Object> mcpServers) {
    }

    static String resolveLocalSourceContentForPass(ReviewTarget target,
                                                   String localSourceContent,
                                                   int passNumber) {
        if (!target.isLocal() || passNumber <= 1) {
            return localSourceContent;
        }
        return null;
    }

    private ReviewTargetInstructionResolver.ResolvedInstruction resolveTargetInstruction(ReviewTarget target) {
        return reviewTargetInstructionResolver.resolve(
            target,
            ctx.cachedResources().sourceContent(),
            ctx.cachedResources().mcpServers()
        );
    }

    private ResolvedReviewParams resolveReviewParams(ReviewTarget target) {
        var resolvedInstruction = resolveTargetInstruction(target);
        return new ResolvedReviewParams(
            target.displayName(),
            resolvedInstruction.instruction(),
            resolvedInstruction.localSourceContent(),
            resolvedInstruction.mcpServers()
        );
    }

    /// Common review execution: configures session, sends instruction, collects result.
    private ReviewResult executeReviewCommon(String displayName,
                                             String instruction,
                                             String localSourceContent,
                                             Map<String, Object> mcpServers,
                                             int currentPass,
                                             int totalPasses) throws Exception {
        String systemPrompt = buildSystemPrompt();
        SessionConfig sessionConfig = reviewSessionConfigFactory.create(
            config,
            ctx,
            systemPrompt,
            mcpServers,
            currentPass,
            totalPasses
        );

        try (var session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutConfig().timeoutMinutes(), TimeUnit.MINUTES)) {
            return executeReviewWithSession(displayName, instruction, localSourceContent, mcpServers, session);
        }
    }

    private ReviewResult executeReviewWithSession(String displayName,
                                                  String instruction,
                                                  String localSourceContent,
                                                  Map<String, Object> mcpServers,
                                                  CopilotSession session) throws Exception {
        String content = sendAndCollectContent(session, instruction, localSourceContent);
        ReviewResult result = reviewResultFactory.fromContent(config, displayName, content, mcpServers != null);
        if (result.success()) {
            logger.info("Review completed for agent: {} (content length: {} chars)",
                config.name(), content.length());
        } else {
            logger.warn("Agent {} returned invalid/empty review output: {}",
                config.name(), result.errorMessage());
        }
        return result;
    }
    
    /// Sends an instruction asynchronously and collects the response content using
    /// activity-based idle timeout instead of a fixed wall-clock deadline.
    ///
    /// Unlike `sendAndWait(options, timeoutMs)` which imposes a hard deadline from the
    /// moment the message is sent, this method uses `send()` (fire-and-forget) combined
    /// with event listeners and an idle timer that resets on every incoming event.
    /// This means MCP tool calls that take a long time but are actively progressing will
    /// NOT cause a timeout — the timeout only fires after `idleTimeoutMinutes` of
    /// complete silence (no events at all).
    ///
    /// Fallback strategies when the final event's content is empty:
    /// 1. Use accumulated content from intermediate `AssistantMessageEvent`s
    /// 2. Send an in-session follow-up prompt — much faster than a full retry
    ///    since MCP context is already loaded
    ///
    /// @param session     the active Copilot session
    /// @param instruction the review instruction to send
    /// @param localSourceContent local source content for local-review targets (nullable)
    /// @return the review content, or null if all strategies failed
    private String sendAndCollectContent(CopilotSession session,
                                         String instruction,
                                         String localSourceContent) throws Exception {
        long idleTimeoutMs = resolveIdleTimeoutMs();
        long maxTimeoutMs = resolveMaxTimeoutMs();

        var messageFlow = createReviewMessageFlow();

        String content = messageFlow.execute(
            instruction,
            localSourceContent,
            prompt -> sendWithActivityTimeout(session, prompt, idleTimeoutMs, maxTimeoutMs)
        );

        return sanitizeReviewContent(content);
    }

    private long resolveIdleTimeoutMs() {
        return TimeUnit.MINUTES.toMillis(ctx.timeoutConfig().idleTimeoutMinutes());
    }

    private long resolveMaxTimeoutMs() {
        return TimeUnit.MINUTES.toMillis(ctx.timeoutConfig().timeoutMinutes());
    }

    private ReviewMessageFlow createReviewMessageFlow() {
        return new ReviewMessageFlow(
            config.name(),
            FOLLOWUP_PROMPT,
            localSourceHeaderPrompt,
            localReviewResultPrompt,
            ctx.agentTuningConfig().instructionBufferExtraCapacity()
        );
    }

    private String sanitizeReviewContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return ContentSanitizer.sanitize(content);
    }

    /// Sends a prompt via the async `send()` API and waits for completion using
    /// an activity-based idle timeout.
    ///
    /// Timeout semantics:
    /// - **Idle timeout** (`idleTimeoutMs`): Resets on every incoming event.
    ///   Only fires if there are no events for this duration. Prevents false timeouts
    ///   during active MCP tool processing.
    /// - **Max timeout** (`maxTimeoutMs`): Absolute wall-clock safety net.
    ///   Prevents runaway sessions regardless of activity.
    private String sendWithActivityTimeout(CopilotSession session, String prompt,
                                           long idleTimeoutMs, long maxTimeoutMs) throws Exception {
        logger.debug("Agent {}: sending prompt asynchronously (idle timeout: {} min, max: {} min)",
            config.name(), ctx.timeoutConfig().idleTimeoutMinutes(), ctx.timeoutConfig().timeoutMinutes());
        return reviewSessionMessageSender.sendWithActivityTimeout(
            prompt,
            maxTimeoutMs,
            sendPrompt -> session.send(new MessageOptions().setPrompt(sendPrompt)),
            collector -> registerEventListeners(session, collector),
            collector -> {
                var scheduledTask = scheduleIdleTimeout(collector, idleTimeoutMs);
                return () -> scheduledTask.cancel(false);
            }
        );
    }

    /// Registers event listeners on the session, wiring them to the content collector.
    private EventSubscriptions registerEventListeners(CopilotSession session, ContentCollector collector) {
        return ReviewSessionEvents.register(
            config.name(),
            collector,
            handler -> session.on(event -> handler.accept(
                new ReviewSessionEvents.EventData(event.getType(), null, 0, null)
            )),
            handler -> session.on(AssistantMessageEvent.class, event -> {
                var data = event.getData();
                int toolCalls = data.toolRequests() != null ? data.toolRequests().size() : 0;
                handler.accept(new ReviewSessionEvents.EventData("assistant", data.content(), toolCalls, null));
            }),
            handler -> session.on(SessionIdleEvent.class, _ ->
                handler.accept(new ReviewSessionEvents.EventData("idle", null, 0, null))),
            handler -> session.on(SessionErrorEvent.class, event -> {
                var data = event.getData();
                handler.accept(new ReviewSessionEvents.EventData(
                    "error", null, 0, data != null ? data.message() : "session error"
                ));
            }),
            trace -> { if (logger.isTraceEnabled()) logger.trace("{}", trace.get()); }
        );
    }

    /// Schedules periodic idle-timeout checks using the shared scheduler.
    private java.util.concurrent.ScheduledFuture<?> scheduleIdleTimeout(
            ContentCollector collector, long idleTimeoutMs) {
        return idleTimeoutScheduler.schedule(ctx.sharedScheduler(), collector, idleTimeoutMs);
    }

    /// Builds the system prompt including output constraints.
    /// Output constraints (CoT suppression, language enforcement) are loaded from an external
    /// template and appended after the base system prompt.
    private String buildSystemPrompt() {
        return reviewSystemPromptFormatter.format(
            AgentPromptBuilder.buildFullSystemPrompt(config, focusAreasGuidance),
            ctx.outputConstraints()
        );
    }
}
