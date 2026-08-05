package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;

import java.nio.file.Path;
import java.util.List;

/// Outbound port: collect and format local source files for review.
///
/// Implementer: {@code infrastructure.file.LocalFileProvider}
/// Callers:     {@code application.review.LocalSourcePrecomputer}
///
/// Covers behaviors: TGT-01–TGT-09
public interface CollectLocalSourcePort {

    /// Collect eligible source files from a local directory.
    ///
    /// Applies the filtering rules in {@code config} (max file size, extensions,
    /// ignored directories, sensitive file exclusions).
    ///
    /// @param directory root directory to traverse
    /// @param config    selection rules
    /// @return candidates in an unspecified order; never null
    List<LocalFileCandidate> collect(Path directory, LocalFileSelectionConfig config);

    /// Format a list of collected file candidates into review-ready content.
    ///
    /// @param candidates the files to format (e.g. with path headers and content blocks)
    /// @return a single string containing all file contents in review format
    String formatContent(List<LocalFileCandidate> candidates);
}
