package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.review.ReviewTarget;

import java.nio.file.Path;

/// Resolves the instruction text and source-content state for a given {@link ReviewTarget}.
///
/// Purified from the old {@code agent.ReviewTargetInstructionResolver}:
/// - Removed {@code @Nullable} (Micronaut), {@code McpServerConfig} (SDK),
///   {@code LocalFileConfig} (infra config), {@code LocalFileProvider} (infra).
/// - Local source collection is no longer performed here; the application layer
///   ({@code LocalSourcePrecomputer}) pre-computes content via {@code CollectLocalSourcePort}
///   and injects it as a plain {@code String}.
/// - MCP server resolution is fully delegated to the application layer.
public final class ReviewTargetInstructionResolver {

    /// The resolved instruction and local source content for a given target.
    ///
    /// @param instruction       the prompt instruction for the agent
    /// @param localSourceContent the embedded source code for local reviews (null for GitHub)
    public record ResolvedInstruction(String instruction, String localSourceContent) {

        /// Whether this is a local-directory review (has embedded source code).
        public boolean isLocal() {
            return localSourceContent != null;
        }
    }

    /// Callback fired when local source content has been resolved.
    @FunctionalInterface
    public interface LocalSourceComputedListener {
        void onComputed();
    }

    private final AgentConfig config;
    private final LocalSourceComputedListener localSourceComputedListener;

    public ReviewTargetInstructionResolver(AgentConfig config) {
        this(config, () -> {});
    }

    public ReviewTargetInstructionResolver(AgentConfig config,
                                           LocalSourceComputedListener localSourceComputedListener) {
        this.config = config;
        this.localSourceComputedListener = localSourceComputedListener != null
            ? localSourceComputedListener
            : () -> {};
    }

    /// Resolves the instruction for the given review target.
    ///
    /// @param target             the review target (local directory or GitHub repository)
    /// @param cachedSourceContent pre-computed source content for local reviews (null triggers notification only)
    /// @return resolved instruction and source content
    public ResolvedInstruction resolve(ReviewTarget target, String cachedSourceContent) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) ->
                resolveLocalInstruction(target, cachedSourceContent);
            case ReviewTarget.GitHubTarget(String repository) ->
                resolveGitHubInstruction(repository);
        };
    }

    private ResolvedInstruction resolveLocalInstruction(ReviewTarget target, String cachedSourceContent) {
        String instruction = AgentPromptBuilder.buildLocalInstructionBase(config, target.displayName());
        if (cachedSourceContent == null) {
            localSourceComputedListener.onComputed();
        }
        return new ResolvedInstruction(instruction, cachedSourceContent);
    }

    private ResolvedInstruction resolveGitHubInstruction(String repository) {
        return new ResolvedInstruction(AgentPromptBuilder.buildInstruction(config, repository), null);
    }
}
