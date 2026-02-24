package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;

import java.nio.file.Path;
import java.util.Map;

/// Resolves instruction payloads for local and GitHub review targets.
final class ReviewTargetInstructionResolver {

    record ResolvedInstruction(
        String instruction,
        String localSourceContent,
        Map<String, Object> mcpServers
    ) {}

    private final AgentConfig config;
    private final ReviewContext context;

    ReviewTargetInstructionResolver(AgentConfig config, ReviewContext context) {
        this.config = config;
        this.context = context;
    }

    ResolvedInstruction resolve(ReviewTarget target) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) -> resolveLocalInstruction(target, directory);
            case ReviewTarget.GitHubTarget(String repository) -> resolveGitHubInstruction(repository);
        };
    }

    private ResolvedInstruction resolveLocalInstruction(ReviewTarget target, Path directory) {
        String sourceContent = resolveLocalSourceContent(directory);
        String instruction = AgentPromptBuilder.buildLocalInstructionBase(config, target.displayName());
        return new ResolvedInstruction(instruction, sourceContent, null);
    }

    private String resolveLocalSourceContent(Path directory) {
        String cachedSource = context.cachedResources().sourceContent();
        if (cachedSource != null) {
            return cachedSource;
        }
        LocalFileProvider fileProvider = new LocalFileProvider(directory, context.localFileConfig());
        var collectionResult = fileProvider.collectAndGenerate();
        return collectionResult.reviewContent();
    }

    private ResolvedInstruction resolveGitHubInstruction(String repository) {
        Map<String, Object> mcpServers = context.cachedResources().mcpServers();
        return new ResolvedInstruction(
            AgentPromptBuilder.buildInstruction(config, repository),
            null,
            mcpServers
        );
    }
}
