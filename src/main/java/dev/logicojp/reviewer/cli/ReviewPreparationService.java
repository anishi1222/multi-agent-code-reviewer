package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Singleton
class ReviewPreparationService {

    private static final DateTimeFormatter OUTPUT_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    @FunctionalInterface
    interface BannerPrinter {
        void print(Map<String, AgentConfig> agentConfigs,
                   List<Path> agentDirs,
                   ModelConfig modelConfig,
                   ReviewTarget target,
                   Path outputDirectory,
                   String reviewModel);
    }

    public record PreparedData(Path outputDirectory) {
    }

    private final BannerPrinter bannerPrinter;
    private final Clock clock;

    @Inject
    public ReviewPreparationService(ReviewOutputFormatter outputFormatter) {
        this(outputFormatter::printBanner, Clock.systemDefaultZone());
    }

    ReviewPreparationService(BannerPrinter bannerPrinter,
                             Clock clock) {
        this.bannerPrinter = bannerPrinter;
        this.clock = clock;
    }

    public PreparedData prepare(ReviewCommand.ParsedOptions options,
                                ReviewTarget target,
                                ModelConfig modelConfig,
                                Map<String, AgentConfig> agentConfigs,
                                List<Path> agentDirs) {
        Path outputDirectory = resolveOutputDirectory(options, target);

        bannerPrinter.print(agentConfigs, agentDirs, modelConfig, target, outputDirectory, options.reviewModel());

        return new PreparedData(outputDirectory);
    }

    private Path resolveOutputDirectory(ReviewCommand.ParsedOptions options, ReviewTarget target) {
        String invocationTimestamp = LocalDateTime.now(clock).format(OUTPUT_TIMESTAMP_FORMATTER);
        return options.outputDirectory()
            .resolve(target.repositorySubPath())
            .resolve(invocationTimestamp);
    }
}
