package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.util.FeatureFlags;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;

public record OrchestratorConfig(
    @Nullable String githubToken,
    @Nullable GithubMcpConfig githubMcpConfig,
    LocalFileConfig localFileConfig,
    FeatureFlags featureFlags,
    ExecutionConfig executionConfig,
    @Nullable String reasoningEffort,
    @Nullable String outputConstraints,
    PromptTexts promptTexts
) {
    public OrchestratorConfig {
        executionConfig = Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        featureFlags = Objects.requireNonNull(featureFlags, "featureFlags must not be null");
        localFileConfig = localFileConfig != null ? localFileConfig : new LocalFileConfig();
        promptTexts = promptTexts != null ? promptTexts : new PromptTexts(null, null, null);
    }

    @Override
    public String toString() {
        return "OrchestratorConfig{githubToken=***, localFileConfig=%s, executionConfig=%s}"
            .formatted(localFileConfig, executionConfig);
    }
}