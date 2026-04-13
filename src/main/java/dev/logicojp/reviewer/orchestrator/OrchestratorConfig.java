package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.service.TemplateService;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;

public record OrchestratorConfig(
    @Nullable String githubToken,
    @Nullable GithubMcpConfig githubMcpConfig,
    LocalFileConfig localFileConfig,
    ExecutionConfig executionConfig,
    @Nullable String reasoningEffort,
    @Nullable String outputConstraints,
    String invocationTimestamp,
    PromptTexts promptTexts,
    RubberDuckConfig rubberDuckConfig,
    @Nullable TemplateService templateService
) {
    public OrchestratorConfig(
        @Nullable String githubToken,
        @Nullable GithubMcpConfig githubMcpConfig,
        LocalFileConfig localFileConfig,
        ExecutionConfig executionConfig,
        @Nullable String reasoningEffort,
        @Nullable String outputConstraints,
        String invocationTimestamp,
        PromptTexts promptTexts,
        RubberDuckConfig rubberDuckConfig
    ) {
        this(githubToken, githubMcpConfig, localFileConfig, executionConfig,
            reasoningEffort, outputConstraints, invocationTimestamp, promptTexts,
            rubberDuckConfig, null);
    }

    public OrchestratorConfig {
        executionConfig = Objects.requireNonNull(executionConfig, "executionConfig must not be null");
        localFileConfig = localFileConfig != null ? localFileConfig : new LocalFileConfig();
        invocationTimestamp = invocationTimestamp != null ? invocationTimestamp : "unknown-start-time";
        promptTexts = promptTexts != null ? promptTexts : new PromptTexts(null, null, null);
        rubberDuckConfig = rubberDuckConfig != null ? rubberDuckConfig : new RubberDuckConfig();
    }

    public boolean isRubberDuckEnabled() {
        return rubberDuckConfig.enabled();
    }

    @Override
    public String toString() {
        return "OrchestratorConfig{githubToken=***, localFileConfig=%s, executionConfig=%s, rubberDuck=%s}"
            .formatted(localFileConfig, executionConfig, rubberDuckConfig.enabled());
    }
}