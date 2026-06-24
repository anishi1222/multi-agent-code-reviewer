package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/// Executes a code review using the Copilot SDK with a specific agent configuration.
public class ReviewAgent {

    private static final Logger logger = LoggerFactory.getLogger(ReviewAgent.class);

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
    private final ReviewTargetInstructionResolver reviewTargetInstructionResolver;
    private final ReviewPassRunner reviewPassRunner;

    /// Creates default collaborators for a given agent configuration and context.
    /// Collaborators are created via `new` rather than DI because they are per-invocation
    /// objects whose lifecycle is bound to a single review execution — not shared singletons.
    /// For testing, use the full-parameter constructor to inject custom collaborators.
    static AgentCollaborators defaultCollaborators(AgentConfig config, ReviewContext ctx) {
        return new AgentCollaborators(
            new ReviewTargetInstructionResolver(
                config,
                ctx.localFileConfig(),
                () -> logger.debug("Computed source content locally for agent: {}", config.name())
            ),
            new ReviewSessionMessageSender(config.name()),
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
                ReviewSystemPromptFormatter reviewSystemPromptFormatter,
                String focusAreasGuidance,
                String localSourceHeaderPrompt,
                String localReviewResultPrompt,
                AgentCollaborators collaborators) {
        this.config = Objects.requireNonNull(config);
        this.ctx = Objects.requireNonNull(ctx);
        this.reviewTargetInstructionResolver = collaborators.reviewTargetInstructionResolver();

        ReviewSessionExecutor sessionExecutor = new ReviewSessionExecutor(
            config,
            ctx,
            reviewSystemPromptFormatter,
            collaborators.reviewSessionMessageSender(),
            collaborators.reviewSessionConfigFactory(),
            collaborators.reviewResultFactory(),
            focusAreasGuidance,
            localSourceHeaderPrompt,
            localReviewResultPrompt
        );
        this.reviewPassRunner = new ReviewPassRunner(
            config,
            ctx,
            collaborators.reviewRetryExecutor(),
            collaborators.reviewResultFactory(),
            sessionExecutor,
            this::resolveReviewParams
        );
    }

    /// Executes the review synchronously on the calling thread with retry support.
    /// Each attempt gets the full configured timeout — the timeout is per-attempt, not cumulative.
    /// @param target The target to review (GitHub repository or local directory)
    /// @return ReviewResult containing the review content
    public ReviewResult review(ReviewTarget target) {
        return reviewPassRunner.review(target);
    }

    /// Executes multiple review passes while reusing a single Copilot session for this agent.
    /// This reduces MCP initialization overhead across passes.
    public List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
        return reviewPassRunner.reviewPasses(target, reviewPasses);
    }

    /// Executes a rubber-duck peer-discussion dialogue for this agent.
    /// Two different models conduct a multi-round discussion, then synthesize
    /// the results into a single unified review.
    ///
    /// @param target           the review target
    /// @param rubberDuckConfig the rubber-duck configuration
    /// @param templateService  the template service for loading dialogue prompts
    /// @return a single ReviewResult containing the synthesized review
    public ReviewResult reviewRubberDuck(ReviewTarget target,
                                         dev.logicojp.reviewer.config.RubberDuckConfig rubberDuckConfig,
                                         dev.logicojp.reviewer.service.TemplateService templateService) {
        var resolvedInstruction = resolveTargetInstruction(target);
        var executor = new RubberDuckDialogueExecutor(
            config, ctx, rubberDuckConfig, templateService);
        return executor.execute(
            target,
            resolvedInstruction.instruction(),
            resolvedInstruction.localSourceContent(),
            resolvedInstruction.mcpServers());
    }

    static String resolveLocalSourceContentForPass(ReviewTarget target,
                                                   String localSourceContent,
                                                   int passNumber) {
        return ReviewPassRunner.resolveLocalSourceContentForPass(target, localSourceContent, passNumber);
    }

    private ReviewTargetInstructionResolver.ResolvedInstruction resolveTargetInstruction(ReviewTarget target) {
        return reviewTargetInstructionResolver.resolve(
            target,
            ctx.cachedResources().sourceContent(),
            ctx.cachedResources().mcpServers()
        );
    }

    private ReviewPassRunner.ResolvedReviewParams resolveReviewParams(ReviewTarget target) {
        var resolvedInstruction = resolveTargetInstruction(target);
        return new ReviewPassRunner.ResolvedReviewParams(
            target.displayName(),
            resolvedInstruction.instruction(),
            resolvedInstruction.localSourceContent(),
            resolvedInstruction.mcpServers()
        );
    }
}
