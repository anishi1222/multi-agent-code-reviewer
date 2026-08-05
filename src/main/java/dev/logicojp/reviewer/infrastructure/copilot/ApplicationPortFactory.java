package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.agent.LoadAgentUseCase;
import dev.logicojp.reviewer.application.auth.ResolveTokenUseCase;
import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import dev.logicojp.reviewer.application.port.inbound.RunDiagnosticsPort;
import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.report.GenerateReportUseCase;
import dev.logicojp.reviewer.application.report.SummaryGenerator.SummaryGenerationConfig;
import dev.logicojp.reviewer.application.review.RunDiagnosticsUseCase;
import dev.logicojp.reviewer.application.skill.ExecuteSkillUseCase;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import dev.logicojp.reviewer.infrastructure.config.AgentPathConfig;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.SkillConfig;
import dev.logicojp.reviewer.infrastructure.config.SummaryConfig;
import dev.logicojp.reviewer.infrastructure.file.ReportFileWriter;
import dev.logicojp.reviewer.infrastructure.parsing.AgentConfigLoader;
import dev.logicojp.reviewer.infrastructure.parsing.SkillRegistry;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Micronaut {@code @Factory} that binds application port interfaces to
/// their infrastructure implementations for DI.
///
/// Each {@code @Singleton} method exposes one inbound or outbound port
/// so that presentation-layer components can depend on the abstraction
/// rather than concrete infrastructure types.
@Factory
public class ApplicationPortFactory {

    /// Provides {@link LoadAgentPort} backed by {@link LoadAgentUseCase}.
    ///
    /// The lambda merges the configured default directories with any
    /// caller-supplied additional paths at runtime.
    @Singleton
    LoadAgentPort loadAgentPort(AgentPathConfig agentPathConfig, SkillConfig skillConfig) {
        List<String> configuredDirs = agentPathConfig.directories() != null
            ? agentPathConfig.directories()
            : List.of();

        return new LoadAgentUseCase(additionalDirs -> {
            List<Path> merged = new ArrayList<>();
            for (String d : configuredDirs) {
                merged.add(Path.of(d));
            }
            if (additionalDirs != null) {
                merged.addAll(additionalDirs);
            }
            try {
                var loader = AgentConfigLoader.builder(merged)
                    .skillConfig(skillConfig)
                    .build();
                return List.copyOf(loader.loadAllAgents().values());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load agent definitions", e);
            }
        });
    }

    /// Provides {@link RunDiagnosticsPort} backed by {@link RunDiagnosticsUseCase}.
    @Singleton
    RunDiagnosticsPort runDiagnosticsPort(ManageCopilotClientPort copilotClient) {
        return new RunDiagnosticsUseCase(copilotClient);
    }

    /// Provides {@link WriteReportPort} backed by {@link ReportFileWriter}.
    @Singleton
    WriteReportPort writeReportPort() {
        return new ReportFileWriter();
    }

    /// Provides {@link GenerateAiSummaryPort} backed by {@link AiSummaryClient}.
    @Singleton
    GenerateAiSummaryPort generateAiSummaryPort(CopilotService copilotService,
                                                 ModelConfig modelConfig,
                                                 ExecutionConfig executionConfig) {
        return new AiSummaryClient(
            copilotService,
            modelConfig.summaryModel(),
            null,
            executionConfig.agentTimeoutMinutes()
        );
    }

    /// Provides {@link RunCopilotSessionPort} backed by {@link ReviewSessionExecutor}.
    ///
    /// Uses {@code "skill"} as default invocation timestamp; callers may override
    /// via {@code SessionRequest.parameters().get("invocationTimestamp")}.
    @Singleton
    RunCopilotSessionPort runCopilotSessionPort(CopilotService copilotService,
                                                 ExecutionConfig executionConfig) {
        return new ReviewSessionExecutor(
            copilotService,
            new ReviewSessionConfigFactory(),
            new ReviewSystemPromptFormatter(),
            executionConfig.agentTimeoutMinutes(),
            "skill"
        );
    }

    /// Provides {@link ExecuteSkillPort} backed by {@link ExecuteSkillUseCase}.
    ///
    /// The use case is skill-registry agnostic: lookup and listing arrive as a function and a
    /// supplier, so the application layer never names {@code infrastructure.parsing.SkillRegistry}.
    /// Same lambda-injection shape as {@link #loadAgentPort}.
    @Singleton
    ExecuteSkillPort executeSkillPort(RunCopilotSessionPort runCopilotSession,
                                       SkillRegistry skillRegistry,
                                       ModelConfig modelConfig) {
        return new ExecuteSkillUseCase(
            runCopilotSession,
            skillRegistry::get,
            skillRegistry::getAll,
            modelConfig.defaultModel()
        );
    }

    /// Provides {@link GenerateReportPort} backed by {@link GenerateReportUseCase}.
    @Singleton
    GenerateReportPort generateReportPort(WriteReportPort writer,
                                           LoadTemplatePort templates,
                                           GenerateAiSummaryPort aiSummary,
                                           SummaryConfig summaryConfig) {
        var config = new SummaryGenerationConfig(
            summaryConfig.maxContentPerAgent(),
            summaryConfig.maxTotalPromptContent(),
            summaryConfig.fallbackExcerptLength(),
            summaryConfig.averageResultContentEstimate(),
            summaryConfig.initialBufferMargin(),
            summaryConfig.excerptNormalizationMultiplier()
        );
        return new GenerateReportUseCase(writer, templates, aiSummary, config);
    }

    /// Provides {@link ResolveTokenPort} backed by {@link ResolveTokenUseCase}.
    ///
    /// The precedence policy lives in the use case; {@link AcquireGitHubTokenPort} supplies only
    /// the mechanisms. The fallback switch is configuration, so it is read here — in the
    /// composition root — and handed to the use case as a plain value, exactly as
    /// {@code defaultModel} is for {@link #executeSkillPort}.
    ///
    /// **Keep this method last.** Micronaut names each generated bean definition after the
    /// method's declaration index (`…$ResolveTokenPort7$Definition`), and
    /// {@code LayerDependencyRulesTest} Rule 4 derives its exemptions from those names. Inserting
    /// a method above this one renumbers every definition that follows it.
    @Singleton
    ResolveTokenPort resolveTokenPort(AcquireGitHubTokenPort tokenSource,
                                       ExecutionConfig executionConfig) {
        return new ResolveTokenUseCase(tokenSource, executionConfig.isGhAuthFallbackEnabled());
    }
}
