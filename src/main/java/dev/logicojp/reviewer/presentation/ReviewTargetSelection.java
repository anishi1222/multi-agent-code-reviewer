package dev.logicojp.reviewer.presentation;

import java.nio.file.Path;

/// Mutually exclusive review target selection parsed from CLI options.
public sealed interface ReviewTargetSelection
    permits ReviewTargetSelection.Repository,
            ReviewTargetSelection.LocalDirectory {

    record Repository(String repository) implements ReviewTargetSelection {}

    record LocalDirectory(Path directory) implements ReviewTargetSelection {}
}
