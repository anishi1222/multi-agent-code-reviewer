package dev.logicojp.reviewer.agent;

import com.github.copilot.rpc.McpServerConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.PromptBudgetConfig;
import dev.logicojp.reviewer.target.LocalFileProvider;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.PromptContentCompactor;
import io.micronaut.core.annotation.Nullable;

import java.nio.file.Path;
import java.util.Map;

final class ReviewTargetInstructionResolver {

    record ResolvedInstruction(String instruction,
                               @Nullable String localSourceContent,
                               @Nullable Map<String, McpServerConfig> mcpServers) {
    }

    @FunctionalInterface
    interface LocalSourceComputedListener {
        void onComputed();
    }

    private final AgentConfig config;
    private final LocalFileConfig localFileConfig;
    private final PromptBudgetConfig promptBudgetConfig;
    private final LocalSourceComputedListener localSourceComputedListener;

    ReviewTargetInstructionResolver(AgentConfig config,
                                    LocalFileConfig localFileConfig,
                                    LocalSourceComputedListener localSourceComputedListener) {
        this.config = config;
        this.localFileConfig = localFileConfig;
        this.promptBudgetConfig = new PromptBudgetConfig();
        this.localSourceComputedListener = localSourceComputedListener;
    }

    ReviewTargetInstructionResolver(AgentConfig config,
                                    LocalFileConfig localFileConfig,
                                    PromptBudgetConfig promptBudgetConfig,
                                    LocalSourceComputedListener localSourceComputedListener) {
        this.config = config;
        this.localFileConfig = localFileConfig;
        this.promptBudgetConfig = promptBudgetConfig != null ? promptBudgetConfig : new PromptBudgetConfig();
        this.localSourceComputedListener = localSourceComputedListener;
    }

    ResolvedInstruction resolve(ReviewTarget target,
                                @Nullable String cachedSourceContent,
                                @Nullable Map<String, McpServerConfig> cachedMcpServers) {
        return switch (target) {
            case ReviewTarget.LocalTarget(Path directory) ->
                resolveLocalInstruction(target, directory, cachedSourceContent);
            case ReviewTarget.GitHubTarget(String repository) ->
                resolveGitHubInstruction(repository, cachedMcpServers);
        };
    }

    private ResolvedInstruction resolveLocalInstruction(ReviewTarget target,
                                                        Path directory,
                                                        @Nullable String cachedSourceContent) {
        String sourceContent = resolveLocalSourceContent(directory, cachedSourceContent);
        String instruction = AgentPromptBuilder.buildLocalInstructionBase(config, target.displayName());
        return new ResolvedInstruction(instruction, sourceContent, null);
    }

    private String resolveLocalSourceContent(Path directory, @Nullable String cachedSourceContent) {
        if (cachedSourceContent != null) {
            return cachedSourceContent;
        }
        LocalFileProvider fileProvider = new LocalFileProvider(directory, localFileConfig);
        var collectionResult = fileProvider.collectAndGenerate();
        localSourceComputedListener.onComputed();
        return compactSourceContent(collectionResult.reviewContent());
    }

    private String compactSourceContent(String sourceContent) {
        if (!promptBudgetConfig.compactPrompts()) {
            return sourceContent;
        }
        return PromptContentCompactor.compactSourceBlocks(
            sourceContent,
            promptBudgetConfig.localSourceMaxChars()
        );
    }

    private ResolvedInstruction resolveGitHubInstruction(String repository,
                                                         @Nullable Map<String, McpServerConfig> cachedMcpServers) {
        return new ResolvedInstruction(AgentPromptBuilder.buildInstruction(config, repository), null, cachedMcpServers);
    }
}