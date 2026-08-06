package dev.logicojp.reviewer.domain.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// A directory to scan for agent definitions, paired with the provenance of everything
/// found inside it.
///
/// This is the type that replaced the bare `List<Path>` on
/// [dev.logicojp.reviewer.application.port.inbound.LoadAgentPort] (ADR-0007 D1). The
/// pairing is the whole point: a `Path` alone cannot answer "how much may I trust the
/// bytes under here?", so every validator downstream of the old signature was forced to
/// guess, and guessed leniently.
///
/// ## Construction
///
/// Instances are created through the two named factories rather than the canonical
/// constructor, so that every construction site reads as a *claim about provenance*
/// that can be reviewed:
///
/// - [#userSupplied(Path)] — only legitimate when the path came from `argv`
///   (`--agents-dir`). The repository under review cannot reach this factory.
/// - [#repositorySupplied(Path)] — for paths derived from the working directory.
///
/// `java.nio.file.Path` in `domain` follows existing precedent
/// (`domain.review.ReviewTarget`, `domain.review.LocalFileCandidate`): it is a JDK value
/// type, not an infrastructure dependency, and no file I/O is performed here.
///
/// @param path   the directory to scan; never null
/// @param source provenance of every definition found under [#path()]; never null
public record AgentSourceDirectory(Path path, AgentSource source) {

    public AgentSourceDirectory {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(source, "source");
    }

    /// A directory the operator named explicitly on the command line (`--agents-dir`).
    ///
    /// @param path directory path taken from `argv`
    /// @return a [AgentSource#USER_SUPPLIED] directory
    public static AgentSourceDirectory userSupplied(Path path) {
        return new AgentSourceDirectory(path, AgentSource.USER_SUPPLIED);
    }

    /// A directory discovered relative to the current working directory, i.e. content of
    /// the repository under review.
    ///
    /// @param path directory path derived from the working directory
    /// @return a [AgentSource#REPOSITORY_SUPPLIED] directory
    public static AgentSourceDirectory repositorySupplied(Path path) {
        return new AgentSourceDirectory(path, AgentSource.REPOSITORY_SUPPLIED);
    }

    /// Wraps each path as [AgentSource#USER_SUPPLIED].
    ///
    /// Convenience for presentation-layer call sites that hold the parsed `--agents-dir`
    /// values. A null or empty input yields an empty list.
    ///
    /// @param paths directory paths taken from `argv`
    /// @return one [AgentSourceDirectory] per input path, in order
    public static List<AgentSourceDirectory> allUserSupplied(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream().map(AgentSourceDirectory::userSupplied).toList();
    }

    /// Whether definitions under this directory are attacker-controlled.
    ///
    /// @return `true` when [#source()] is [AgentSource#REPOSITORY_SUPPLIED]
    public boolean isUntrusted() {
        return source.isUntrusted();
    }
}
