package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// Prints CLI banner and startup summary for review execution.
final class ReviewBannerPrinter {

    private final CliOutput output;
    private final ExecutionConfig executionConfig;

    ReviewBannerPrinter(CliOutput output, ExecutionConfig executionConfig) {
        this.output = output;
        this.executionConfig = executionConfig;
    }

    void printBanner(Map<String, AgentConfig> agentConfigs,
                     List<Path> agentDirs,
                     ModelConfig modelConfig,
                     ReviewTarget target,
                     Path outputDirectory,
                     String reviewModel) {
        output.println("╔════════════════════════════════════════════════════════════╗");
        output.println("║           Multi-Agent Code Reviewer                        ║");
        output.println("╚════════════════════════════════════════════════════════════╝");
        output.println("");
        output.println("Target: " + target.displayName() + (target.isLocal() ? " (local)" : " (GitHub)"));
        output.println("Agents: " + agentConfigs.keySet());
        output.println("Output: " + outputDirectory.toAbsolutePath());
        output.println("");
        output.println("Agent directories:");
        for (Path dir : agentDirs) {
            output.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        output.println("");
        output.println("Models:");
        output.println("  Review: " + (reviewModel != null ? reviewModel : "(agent default)"));
        output.println("  Summary: " + modelConfig.summaryModel());
        if (executionConfig.reviewPasses() > 1) {
            output.println("Review passes: " + executionConfig.reviewPasses() + " per agent");
        }
        output.println("");
    }

    void printNoAgentsError(List<Path> agentDirs) {
        output.errorln("Error: No agents found. Check the agents directories:");
        for (Path dir : agentDirs) {
            output.errorln("  - " + dir);
        }
    }
}
