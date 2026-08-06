package dev.logicojp.reviewer.presentation.formatter;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.presentation.CliOutput;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Formats and prints review command output for banners and completion summaries.
@Singleton
public class ReviewOutputFormatter {

    private final CliOutput output;
    private final int reviewPasses;

    @Inject
    public ReviewOutputFormatter(
            CliOutput output,
            @Value("${reviewer.execution.review-passes:1}") int reviewPasses) {
        this.output = output;
        this.reviewPasses = reviewPasses;
    }

    /// Prints the review startup banner.
    ///
    /// @param agentConfigs   loaded agent configs
    /// @param agentDirs      directories searched for agents
    /// @param summaryModel   effective summary model (from config or CLI override)
    /// @param target         review target
    /// @param outputDirectory resolved output directory
    /// @param reviewModel    CLI-provided review model override (null = agent default)
    public void printBanner(Map<String, AgentConfig> agentConfigs,
                            List<Path> agentDirs,
                            String summaryModel,
                            ReviewTarget target,
                            Path outputDirectory,
                            String reviewModel) {
        printBannerHeader();
        printTargetSection(target, agentConfigs, outputDirectory);
        printAgentDirectories(agentDirs);
        printModelSection(summaryModel, reviewModel);
        if (reviewPasses > 1) {
            output.println("Review passes: " + reviewPasses + " per agent");
        }
        printBlankLine();
    }

    public void printCompletionSummary(List<ReviewResult> results, Path outputDirectory) {
        long successCount = results.stream().filter(ReviewResult::success).count();
        output.println("");
        output.println("════════════════════════════════════════════════════════════");
        output.println("Review completed!");
        output.println("  Total agents: " + results.size());
        output.println("  Successful: " + successCount);
        output.println("  Failed: " + (results.size() - successCount));
        output.println("  Reports: " + outputDirectory.toAbsolutePath());
        output.println("════════════════════════════════════════════════════════════");
    }

    private void printBannerHeader() {
        output.println("╔════════════════════════════════════════════════════════════╗");
        output.println("║           Multi-Agent Code Reviewer                        ║");
        output.println("╚════════════════════════════════════════════════════════════╝");
        printBlankLine();
    }

    private void printTargetSection(ReviewTarget target,
                                    Map<String, AgentConfig> agentConfigs,
                                    Path outputDirectory) {
        output.println("Target: " + target.displayName() +
            (target.isLocal() ? " (local)" : " (GitHub)"));
        output.println("Agents: " + agentConfigs.keySet());
        output.println("Output: " + outputDirectory.toAbsolutePath());
        printBlankLine();
    }

    private void printAgentDirectories(List<Path> agentDirs) {
        output.println("Agent directories:");
        for (Path dir : agentDirs) {
            output.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        printBlankLine();
    }

    private void printModelSection(String summaryModel, String reviewModel) {
        output.println("Models:");
        output.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        output.println("  Summary: " + (summaryModel != null ? summaryModel : "(configured default)"));
    }

    private void printBlankLine() {
        output.println("");
    }
}
