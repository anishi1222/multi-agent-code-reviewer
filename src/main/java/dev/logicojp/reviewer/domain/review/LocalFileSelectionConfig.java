package dev.logicojp.reviewer.domain.review;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Immutable configuration governing which local files are eligible for review.
///
/// The {@code from(LocalFileConfig)} factory method has been removed from this domain
/// type — it is created by the infrastructure layer ({@code LocalFileProvider}) which
/// has access to {@code LocalFileConfig}.
public record LocalFileSelectionConfig(
    long maxFileSize,
    long maxTotalSize,
    Set<String> ignoredDirectories,
    Set<String> sourceExtensions,
    Set<String> sensitiveFilePatterns,
    Set<String> sensitiveExtensions
) {

    /// Builds a {@link LocalFileSelectionConfig} from raw list-based values.
    /// All string values are lower-cased and blank entries removed.
    public static LocalFileSelectionConfig of(
        long maxFileSize,
        long maxTotalSize,
        List<String> ignoredDirectories,
        List<String> sourceExtensions,
        List<String> sensitiveFilePatterns,
        List<String> sensitiveExtensions
    ) {
        return new LocalFileSelectionConfig(
            maxFileSize,
            maxTotalSize,
            normalizeSet(ignoredDirectories),
            normalizeSet(sourceExtensions),
            normalizeSet(sensitiveFilePatterns),
            normalizeSet(sensitiveExtensions)
        );
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .map(LocalFileSelectionConfig::normalizeValue)
            .flatMap(Optional::stream)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.toLowerCase(Locale.ROOT));
    }
}
