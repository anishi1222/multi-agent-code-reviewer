package dev.logicojp.reviewer.domain.review;

import java.nio.file.Path;

/// Represents a candidate file for local-directory review.
///
/// @param path the resolved file path
/// @param size the file size in bytes
public record LocalFileCandidate(Path path, long size) {
}
