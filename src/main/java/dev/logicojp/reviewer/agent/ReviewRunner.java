package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpServerConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

final class ReviewRunner {

    @FunctionalInterface
    interface ReviewParamsResolver {
        ResolvedReviewParams resolve(ReviewTarget target);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewRunner.class);

    private final AgentConfig config;
    private final ReviewRetryExecutor reviewRetryExecutor;
    private final ReviewResultFactory reviewResultFactory;
    private final ReviewSessionExecutor reviewSessionExecutor;
    private final ReviewParamsResolver paramsResolver;

    ReviewRunner(AgentConfig config,
                 ReviewRetryExecutor reviewRetryExecutor,
                 ReviewResultFactory reviewResultFactory,
                 ReviewSessionExecutor reviewSessionExecutor,
                 ReviewParamsResolver paramsResolver) {
        this.config = Objects.requireNonNull(config);
        this.reviewRetryExecutor = Objects.requireNonNull(reviewRetryExecutor);
        this.reviewResultFactory = Objects.requireNonNull(reviewResultFactory);
        this.reviewSessionExecutor = Objects.requireNonNull(reviewSessionExecutor);
        this.paramsResolver = Objects.requireNonNull(paramsResolver);
    }

    ReviewResult review(ReviewTarget target) {
        return reviewRetryExecutor.execute(
            () -> executeReview(target),
            exception -> reviewResultFactory.fromException(config, target.displayName(), exception)
        );
    }

    private ReviewResult executeReview(ReviewTarget target) throws Exception {
        logger.info("Starting review with agent: {} for target: {}",
            config.name(), target.displayName());

        ResolvedReviewParams params = paramsResolver.resolve(target);
        return reviewSessionExecutor.execute(new ReviewSessionExecutor.Request(
            params.displayName(),
            params.instruction(),
            params.localSourceContent(),
            params.mcpServers()
        ));
    }

    record ResolvedReviewParams(String displayName,
                                String instruction,
                                String localSourceContent,
                                Map<String, McpServerConfig> mcpServers) {
    }
}
