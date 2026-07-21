package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.PromptContentCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

final class LocalSourcePrecomputer {

    private static final Logger logger = LoggerFactory.getLogger(LocalSourcePrecomputer.class);
    private final LocalSourceCollectorFactory localSourceCollectorFactory;
    private final LocalFileConfig localFileConfig;
    private final PromptBudgetConfig promptBudgetConfig;

    LocalSourcePrecomputer(LocalSourceCollectorFactory localSourceCollectorFactory,
                           LocalFileConfig localFileConfig) {
        this(localSourceCollectorFactory, localFileConfig, new PromptBudgetConfig());
    }

    LocalSourcePrecomputer(LocalSourceCollectorFactory localSourceCollectorFactory,
                           LocalFileConfig localFileConfig,
                           PromptBudgetConfig promptBudgetConfig) {
        this.localSourceCollectorFactory = localSourceCollectorFactory;
        this.localFileConfig = localFileConfig;
        this.promptBudgetConfig = promptBudgetConfig != null ? promptBudgetConfig : new PromptBudgetConfig();
    }

    Optional<String> preComputeSourceContent(ReviewTarget target) {
        Optional<Path> directory = resolveLocalDirectory(target);
        if (directory.isEmpty()) {
            return Optional.empty();
        }

        logPrecomputeStart(directory.get());
        var collection = localSourceCollectorFactory.create(directory.get(), localFileConfig).collectAndGenerate();
        logCollectionResult(collection.fileCount(), collection.directorySummary());
        return Optional.ofNullable(compactSourceContent(collection.reviewContent()));
    }

    private String compactSourceContent(String sourceContent) {
        if (!promptBudgetConfig.compactPrompts()) {
            return sourceContent;
        }
        return PromptContentCompactor.compactSourceBlocks(
            sourceContent,
            promptBudgetConfig.localSourceMaxChars()
        );
    }

    private Optional<Path> resolveLocalDirectory(ReviewTarget target) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> Optional.of(directory);
            case ReviewTarget.GitHubTarget(_) -> Optional.empty();
        };
    }

    private void logPrecomputeStart(Path directory) {
        logger.info("Pre-computing source content for local directory: {}", directory);
    }

    private void logCollectionResult(int fileCount, String directorySummary) {
        logger.info("Collected {} source files from local directory", fileCount);
        logger.debug("Directory summary:\n{}", directorySummary);
    }
}