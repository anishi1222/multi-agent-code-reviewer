package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.outbound.GenerateAiSummaryPort;
import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/// Infrastructure-owned construction of SDK-backed outbound adapters.
@Factory
public final class CopilotPortFactory {

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
}
