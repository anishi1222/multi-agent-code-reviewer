package dev.logicojp.reviewer.target;

import dev.logicojp.reviewer.config.ReviewerConfig;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Immutable selection configuration for local file collection.
record LocalFileSelectionConfig(
    long maxFileSize,
    long maxTotalSize,
    Set<String> ignoredDirectories,
    Set<String> sourceExtensions,
    Set<String> sensitiveFilePatterns,
    Set<String> sensitiveExtensions
) {

    static LocalFileSelectionConfig from(ReviewerConfig.LocalFiles config) {
        return new LocalFileSelectionConfig(
            config.maxFileSize(),
            config.maxTotalSize(),
            normalizeSet(config.ignoredDirectories()),
            normalizeSet(config.sourceExtensions()),
            normalizeSet(config.sensitiveFilePatterns()),
            normalizeSet(config.sensitiveExtensions())
        );
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return values.stream()
            .map(LocalFileSelectionConfig::normalizeValue)
            .flatMap(Optional::stream)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> normalizeValue(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.of(value.toLowerCase(Locale.ROOT));
    }
}
