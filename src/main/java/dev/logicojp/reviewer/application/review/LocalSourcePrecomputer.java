package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.CollectLocalSourcePort;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.shared.PromptBudget;
import dev.logicojp.reviewer.shared.PromptContentCompactor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/// Pre-computes local source content before agent review execution.
///
/// Purified from {@code orchestrator.LocalSourcePrecomputer}:
/// - Replaced {@code LocalSourceCollectorFactory} + {@code LocalFileConfig} with
///   {@code CollectLocalSourcePort} (dependency-inversion principle).
/// - Replaced SLF4J with {@code java.util.logging}.
/// - Applies prompt-budget source compaction (ported from `origin/main` `38dcbc8`).
///   Main compacted at two collection points ({@code ReviewTargetInstructionResolver} and
///   {@code LocalSourcePrecomputer}); the layered tree collects local source only here, so
///   this is the single compaction point.
public final class LocalSourcePrecomputer {

    private static final Logger logger = Logger.getLogger(LocalSourcePrecomputer.class.getName());

    private final CollectLocalSourcePort collectLocalSource;
    private final PromptBudget promptBudget;

    public LocalSourcePrecomputer(CollectLocalSourcePort collectLocalSource) {
        this(collectLocalSource, new PromptBudget());
    }

    public LocalSourcePrecomputer(CollectLocalSourcePort collectLocalSource, PromptBudget promptBudget) {
        this.collectLocalSource = collectLocalSource;
        this.promptBudget = promptBudget != null ? promptBudget : new PromptBudget();
    }

    /// Pre-computes source content for local-directory reviews.
    ///
    /// Returns {@code Optional.empty()} for GitHub (non-local) targets.
    ///
    /// @param target          the review target
    /// @param selectionConfig file selection rules (null → default rules)
    /// @return the formatted source content, or empty for GitHub targets
    public Optional<String> preComputeSourceContent(ReviewTarget target, LocalFileSelectionConfig selectionConfig) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) ->
                Optional.ofNullable(collectLocalSource(directory, selectionConfig));
            case ReviewTarget.GitHubTarget(_) -> Optional.empty();
        };
    }

    private String collectLocalSource(Path directory, LocalFileSelectionConfig config) {
        if (config == null) {
            logger.warning(() -> "No LocalFileSelectionConfig provided for " + directory + "; using no-filter collection");
        }
        logger.info(() -> "Pre-computing source content for local directory: " + directory);
        var candidates = collectLocalSource.collect(directory, config);
        logger.info(() -> "Collected " + candidates.size() + " source file(s) from local directory");
        return compactSourceContent(collectLocalSource.formatContent(candidates));
    }

    private String compactSourceContent(String sourceContent) {
        if (sourceContent == null || !promptBudget.compactPrompts()) {
            return sourceContent;
        }
        return PromptContentCompactor.compactSourceBlocks(
            sourceContent,
            promptBudget.localSourceMaxChars()
        );
    }
}
