package dev.logicojp.reviewer.presentation;

import java.nio.file.Path;
import java.util.List;

/// Parsed CLI options for a skill execution run.
///
/// Lives in the presentation root package alongside {@link ReviewOptions} so that
/// `presentation.parser` (which produces it) and `presentation.command` (which consumes it)
/// depend on a shared type rather than on each other. See `LayerDependencyRulesTest`
/// Rule 6b: sibling sub-packages of a layer must not form dependency cycles.
public record SkillOptions(
    String skillId,
    List<String> paramStrings,
    String githubToken,
    String model,
    List<Path> additionalAgentDirs,
    boolean listSkills
) {
    public SkillOptions {
        paramStrings = paramStrings != null ? List.copyOf(paramStrings) : List.of();
        additionalAgentDirs = additionalAgentDirs != null ? List.copyOf(additionalAgentDirs) : List.of();
    }
}
