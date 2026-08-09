package dev.logicojp.reviewer;

import dev.logicojp.reviewer.application.agent.LoadAgentUseCase;
import dev.logicojp.reviewer.application.auth.ResolveTokenUseCase;
import dev.logicojp.reviewer.application.port.inbound.ConfigureLoggingPort;
import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.application.port.inbound.GenerateReportPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import dev.logicojp.reviewer.application.port.inbound.RunDiagnosticsPort;
import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;
import dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort;
import dev.logicojp.reviewer.application.port.outbound.LoadAgentDefinitionsPort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;
import dev.logicojp.reviewer.application.port.outbound.ManageSkillCatalogPort;
import dev.logicojp.reviewer.application.port.outbound.ResolveApplicationSettingsPort;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.application.port.outbound.SetLogLevelPort;
import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import dev.logicojp.reviewer.application.report.GenerateReportUseCase;
import dev.logicojp.reviewer.application.review.DescribeReviewPlanUseCase;
import dev.logicojp.reviewer.application.review.RunDiagnosticsUseCase;
import dev.logicojp.reviewer.application.skill.ExecuteSkillUseCase;
import dev.logicojp.reviewer.application.startup.ConfigureLoggingUseCase;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/// Layer-zero bindings from inbound ports to application use cases.
///
/// Configuration mapping, external I/O, and SDK adapter construction live in infrastructure.
/// This factory only joins already-constructed ports and plain values into application objects.
@Factory
public final class ApplicationPortFactory {

    @Singleton
    LoadAgentPort loadAgentPort(LoadAgentDefinitionsPort definitions) {
        return new LoadAgentUseCase(definitions);
    }

    @Singleton
    RunDiagnosticsPort runDiagnosticsPort(ManageCopilotClientPort copilotClient) {
        return new RunDiagnosticsUseCase(copilotClient);
    }

    @Singleton
    ExecuteSkillPort executeSkillPort(RunCopilotSessionPort runCopilotSession,
                                       ManageSkillCatalogPort skillCatalog,
                                       ResolveApplicationSettingsPort settings) {
        return new ExecuteSkillUseCase(
            runCopilotSession,
            skillCatalog,
            settings.defaultSkillModel()
        );
    }

    @Singleton
    GenerateReportPort generateReportPort(WriteReportPort writer,
                                           LoadTemplatePort templates,
                                           GenerateAiSummaryPort aiSummary,
                                           ResolveApplicationSettingsPort settings) {
        return new GenerateReportUseCase(writer, templates, aiSummary, settings.summarySettings());
    }

    @Singleton
    ResolveTokenPort resolveTokenPort(AcquireGitHubTokenPort tokenSource,
                                       ResolveApplicationSettingsPort settings) {
        return new ResolveTokenUseCase(tokenSource, settings.ghAuthFallbackEnabled());
    }

    @Singleton
    DescribeReviewPlanPort describeReviewPlanPort(ResolveApplicationSettingsPort settings) {
        return new DescribeReviewPlanUseCase(settings);
    }

    @Singleton
    ConfigureLoggingPort configureLoggingPort(SetLogLevelPort logLevel) {
        return new ConfigureLoggingUseCase(logLevel);
    }
}
