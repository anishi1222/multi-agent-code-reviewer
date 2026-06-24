package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpServerConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;

final class ReviewPassRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPassRunner.class);

    @FunctionalInterface
    interface ReviewParamsResolver {
        ResolvedReviewParams resolve(ReviewTarget target);
    }

    private record PassResult(int passNumber, ReviewResult result) {
    }

    private final AgentConfig config;
    private final ReviewContext ctx;
    private final ReviewRetryExecutor reviewRetryExecutor;
    private final ReviewResultFactory reviewResultFactory;
    private final ReviewSessionExecutor reviewSessionExecutor;
    private final ReviewParamsResolver paramsResolver;

    ReviewPassRunner(AgentConfig config,
                     ReviewContext ctx,
                     ReviewRetryExecutor reviewRetryExecutor,
                     ReviewResultFactory reviewResultFactory,
                     ReviewSessionExecutor reviewSessionExecutor,
                     ReviewParamsResolver paramsResolver) {
        this.config = Objects.requireNonNull(config);
        this.ctx = Objects.requireNonNull(ctx);
        this.reviewRetryExecutor = Objects.requireNonNull(reviewRetryExecutor);
        this.reviewResultFactory = Objects.requireNonNull(reviewResultFactory);
        this.reviewSessionExecutor = Objects.requireNonNull(reviewSessionExecutor);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
    }

    ReviewResult review(ReviewTarget target) {
        return reviewForPass(target, 1, 1);
    }

    List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
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

    private ReviewResult reviewForPass(ReviewTarget target, int currentPass, int totalPasses) {
        return reviewRetryExecutor.execute(
            () -> executeReview(target, currentPass, totalPasses),
            e -> reviewResultFactory.fromException(config, target.displayName(), e)
        );
    }

    private ReviewResult executeReview(ReviewTarget target, int currentPass, int totalPasses) throws Exception {
        logger.info("Starting review with agent: {} for target: {}",
            config.name(), target.displayName());

        ResolvedReviewParams params = paramsResolver.resolve(target);
        return reviewSessionExecutor.execute(request(params, currentPass, totalPasses));
    }

    private List<ReviewResult> executeReviewPassesFallback(ReviewTarget target, int reviewPasses) {
        try (var scope = StructuredConcurrencyUtils.<ReviewResult>openAwaitAllScope()) {
            List<StructuredTaskScope.Subtask<ReviewResult>> tasks = new ArrayList<>(reviewPasses);
            for (int pass = 1; pass <= reviewPasses; pass++) {
                int passNumber = pass;
                tasks.add(scope.fork(() -> reviewForPass(target, passNumber, reviewPasses)));
            }

            StructuredConcurrencyUtils.join(scope);
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

    private List<ReviewResult> executeReviewPasses(ReviewTarget target, int reviewPasses) throws Exception {
        if (reviewPasses <= 2) {
            return executeReviewPassesSequential(target, reviewPasses);
        }
        return executeReviewPassesHybrid(target, reviewPasses);
    }

    private List<ReviewResult> executeReviewPassesSequential(ReviewTarget target, int reviewPasses) throws Exception {
        logger.info("Starting {} review passes with shared session for agent: {} on target: {}",
            reviewPasses, config.name(), target.displayName());

        ResolvedReviewParams params = paramsResolver.resolve(target);
        var sessionConfig = reviewSessionExecutor.createSessionConfig(request(params, 1, reviewPasses));

        try (var session = ctx.client().createSession(sessionConfig)
            .get(ctx.timeoutConfig().timeoutMinutes(), TimeUnit.MINUTES)) {
            List<ReviewResult> results = new ArrayList<>(reviewPasses);
            for (int pass = 1; pass <= reviewPasses; pass++) {
                int passNumber = pass;
                ReviewSessionExecutor.Request request = request(
                    params.withLocalSourceContent(resolveLocalSourceContentForPass(
                        target,
                        params.localSourceContent(),
                        passNumber
                    )),
                    passNumber,
                    reviewPasses
                );
                logger.debug("Agent {}: executing pass {}/{} on shared session",
                    config.name(), passNumber, reviewPasses);
                ReviewResult result = reviewRetryExecutor.execute(
                    () -> reviewSessionExecutor.executeWithSession(request, session),
                    e -> reviewResultFactory.fromException(config, params.displayName(), e)
                );
                results.add(result);
            }
            return results;
        }
    }

    private List<ReviewResult> executeReviewPassesHybrid(ReviewTarget target, int reviewPasses) throws Exception {
        logger.info("Starting {} review passes with hybrid mode for agent: {} on target: {}",
            reviewPasses, config.name(), target.displayName());

        ResolvedReviewParams params = paramsResolver.resolve(target);
        List<ReviewResult> results = new ArrayList<>(reviewPasses);

        ResolvedReviewParams firstPassParams = params.withLocalSourceContent(
            resolveLocalSourceContentForPass(target, params.localSourceContent(), 1));
        results.add(reviewRetryExecutor.execute(
            () -> reviewSessionExecutor.execute(request(firstPassParams, 1, reviewPasses)),
            e -> reviewResultFactory.fromException(config, params.displayName(), e)
        ));

        if (reviewPasses > 1) {
            results.addAll(submitAndCollectParallelPasses(target, params, reviewPasses));
        }

        return results;
    }

    private List<ReviewResult> submitAndCollectParallelPasses(
            ReviewTarget target, ResolvedReviewParams params, int reviewPasses)
            throws InterruptedException {
        try (var scope = StructuredConcurrencyUtils.<PassResult>openAwaitAllScope()) {
            List<StructuredTaskScope.Subtask<PassResult>> tasks = new ArrayList<>(reviewPasses - 1);
            for (int pass = 2; pass <= reviewPasses; pass++) {
                int passNumber = pass;
                tasks.add(scope.fork(() -> executeParallelPass(target, params, passNumber, reviewPasses)));
            }

            StructuredConcurrencyUtils.join(scope);
            return collectParallelPassResults(tasks);
        }
    }

    private PassResult executeParallelPass(ReviewTarget target,
                                           ResolvedReviewParams params,
                                           int passNumber,
                                           int totalPasses) {
        ReviewResult result = reviewRetryExecutor.execute(
            () -> reviewSessionExecutor.execute(request(params, passNumber, totalPasses)),
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

    static String resolveLocalSourceContentForPass(ReviewTarget target,
                                                   String localSourceContent,
                                                   int passNumber) {
        if (!target.isLocal() || passNumber <= 1) {
            return localSourceContent;
        }
        return null;
    }

    private ReviewSessionExecutor.Request request(ResolvedReviewParams params, int currentPass, int totalPasses) {
        return new ReviewSessionExecutor.Request(
            params.displayName(),
            params.instruction(),
            params.localSourceContent(),
            params.mcpServers(),
            currentPass,
            totalPasses
        );
    }

    record ResolvedReviewParams(String displayName,
                                String instruction,
                                String localSourceContent,
                                Map<String, McpServerConfig> mcpServers) {
        ResolvedReviewParams withLocalSourceContent(String value) {
            return new ResolvedReviewParams(displayName, instruction, value, mcpServers);
        }
    }
}
