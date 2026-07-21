package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Formats and prints review command output for banners and completion summaries.
@Singleton
class ReviewOutputFormatter {

    private final CliOutput output;

    @Inject
    public ReviewOutputFormatter(CliOutput output) {
        this.output = output;
    }

    public void printBanner(Map<String, AgentConfig> agentConfigs,
                            List<Path> agentDirs,
                            ModelConfig modelConfig,
                            ReviewTarget target,
                            Path outputDirectory,
                            String reviewModel) {
        printBannerHeader();
        printTargetSection(target, agentConfigs, outputDirectory);
        printAgentDirectories(agentDirs);
        printModelSection(modelConfig, reviewModel);
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

    private void printModelSection(ModelConfig modelConfig, String reviewModel) {
        output.println("Models:");
        output.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        output.println("  Summary: " + modelConfig.summaryModel());
    }

    private void printBlankLine() {
        output.println("");
    }
}
