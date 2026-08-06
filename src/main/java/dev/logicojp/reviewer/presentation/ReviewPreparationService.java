package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Handles pre-execution validation and banner display for the review flow.
@Singleton
public class ReviewPreparationService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewPreparationService.class);

    private final ReviewOutputFormatter bannerPrinter;
    private final DescribeReviewPlanPort reviewPlanSource;

    @Inject
    public ReviewPreparationService(ReviewOutputFormatter bannerPrinter,
                                    DescribeReviewPlanPort reviewPlanSource) {
        this.bannerPrinter = Objects.requireNonNull(bannerPrinter, "bannerPrinter must not be null");
        this.reviewPlanSource =
            Objects.requireNonNull(reviewPlanSource, "reviewPlanSource must not be null");
    }

    /// Validates agent and target selection, prints startup banner.
    ///
    /// The banner's pass count is not a parameter: it is read from
    /// {@link DescribeReviewPlanPort} at print time, so it always reflects the plan the executor
    /// will follow rather than a separately-bound configuration key (t24/F3).
    ///
    /// @param agentDirs      additional agent directories from CLI (for display)
    /// @param agents         resolved agent configs
    /// @param target         resolved review target
    /// @param outputDir      output directory for reports
    /// @param summaryModel   effective summary model string (from config, no infrastructure import)
    /// @param reviewModel    CLI review model override (may be null)
    /// @throws CliValidationException if no agents are found
    public void prepare(List<Path> agentDirs,
                        Map<String, AgentConfig> agents,
                        ReviewTarget target,
                        Path outputDir,
                        String summaryModel,
                        String reviewModel) {
        if (agents.isEmpty()) {
            logger.warn("No agent configurations found in agent directories: {}", agentDirs);
            throw new CliValidationException(
                "No review agents found. Verify agent directories: " + agentDirs, true);
        }

        bannerPrinter.printBanner(agents, agentDirs, summaryModel, target, outputDir, reviewModel,
            reviewPlanSource.describePlan());
    }
}
