package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.agent.LoadAgentUseCase;
import dev.logicojp.reviewer.application.auth.ResolveTokenUseCase;
import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
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
import dev.logicojp.reviewer.application.review.DescribeReviewPlanUseCase;
import dev.logicojp.reviewer.application.review.RunDiagnosticsUseCase;
import dev.logicojp.reviewer.application.skill.ExecuteSkillUseCase;
import dev.logicojp.reviewer.domain.agent.AgentSource;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
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
import dev.logicojp.reviewer.infrastructure.config.PromptBudgetConfig;

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
    /// ## The trust boundary lives here
    ///
    /// This method is the composition root's *only* assignment of agent-definition
    /// provenance (ADR-0007 D1). Two populations of directories meet here and they are not
    /// equally trustworthy:
    ///
    /// - `agentPathConfig.directories()` are resolved relative to the working directory, so
    ///   for a review run they resolve inside the repository under review. Their contents are
    ///   attacker-controlled whenever the reviewed repository is (boundary B3) and are
    ///   therefore tagged {@link AgentSource#REPOSITORY_SUPPLIED}.
    /// - `additionalDirs` reach this lambda already tagged by the caller, because they
    ///   originate from `--agents-dir` on the command line (boundary B1) and the reviewed
    ///   repository cannot influence argv.
    ///
    /// Previously both were flattened into one `List&lt;Path&gt;` here. That merge destroyed
    /// provenance, which is precisely why every downstream validator had to apply a single
    /// permissive limit and why the strict limits were never wired up (SEC-H1/SEC-H2).
    /// Keeping the two populations distinguishable is the whole point of this change: nothing
    /// downstream re-derives trust, it only reads what was decided here.
    @Singleton
    LoadAgentPort loadAgentPort(AgentPathConfig agentPathConfig, SkillConfig skillConfig) {
        List<String> configuredDirs = agentPathConfig.directories() != null
            ? agentPathConfig.directories()
            : List.of();

        return new LoadAgentUseCase(additionalDirs -> {
            List<AgentSourceDirectory> merged = new ArrayList<>();
            for (String d : configuredDirs) {
                merged.add(AgentSourceDirectory.repositorySupplied(Path.of(d)));
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
                                           SummaryConfig summaryConfig,
                                           PromptBudgetConfig promptBudgetConfig) {
        var config = new SummaryGenerationConfig(
            summaryConfig.maxContentPerAgent(),
            summaryConfig.maxTotalPromptContent(),
            summaryConfig.fallbackExcerptLength(),
            summaryConfig.averageResultContentEstimate(),
            summaryConfig.initialBufferMargin(),
            summaryConfig.excerptNormalizationMultiplier(),
            promptBudgetConfig.toPromptBudget()
        );
        return new GenerateReportUseCase(writer, templates, aiSummary, config);
    }

    /// Provides {@link ResolveTokenPort} backed by {@link ResolveTokenUseCase}.
    ///
    /// The precedence policy lives in the use case; {@link AcquireGitHubTokenPort} supplies only
    /// the mechanisms. The fallback switch is configuration, so it is read here — in the
    /// composition root — and handed to the use case as a plain value, exactly as
    /// {@code defaultModel} is for {@link #executeSkillPort}.
    @Singleton
    ResolveTokenPort resolveTokenPort(AcquireGitHubTokenPort tokenSource,
                                       ExecutionConfig executionConfig) {
        return new ResolveTokenUseCase(tokenSource, executionConfig.isGhAuthFallbackEnabled());
    }

    /// Provides {@link DescribeReviewPlanPort} backed by {@link DescribeReviewPlanUseCase}.
    ///
    /// The supplier is bound to {@link ExecutionConfig#reviewPasses()} — the **same accessor**
    /// {@code ReviewContextFactory} resolves when it maps configuration onto
    /// {@code OrchestratorConfig} for an actual run. That is the whole point of the binding: the
    /// banner and the executor cannot read different keys because they no longer read a key at
    /// all, they read one accessor.
    ///
    /// Before t28 the banner bound `reviewer.execution.review-passes` from `presentation`, a key
    /// nothing else read, while the executor used `reviewer.execution.concurrency.review-passes`
    /// (t24/F3). `ReviewPassesSingleSourceTest` is the control that keeps them agreeing.
    ///
    /// **Append new methods below this one.** Micronaut names each generated bean definition
    /// after the method's declaration index (`…$ResolveTokenPort7$Definition`). Inserting a
    /// method *above* an existing one renumbers every definition that follows; appending at the
    /// end leaves indices 0..n-1 stable. {@code LayerDependencyRulesTest} Rule 4 derives its
    /// exemptions from the declaring class rather than those names, so it survives renumbering —
    /// but external tooling that pins a definition name does not.
    @Singleton
    DescribeReviewPlanPort describeReviewPlanPort(ExecutionConfig executionConfig) {
        return new DescribeReviewPlanUseCase(executionConfig::reviewPasses);
    }
}
