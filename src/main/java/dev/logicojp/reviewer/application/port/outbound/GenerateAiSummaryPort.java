package dev.logicojp.reviewer.application.port.outbound;

import java.util.Optional;

/// Outbound port: generate an AI-powered executive summary.
///
/// Implementer: {@code infrastructure.copilot.AiSummaryClient}
/// Callers:     {@code application.report.SummaryGenerator}
///
/// Cycle resolution: breaks cycle 10 (report.summary ⇄ service) by removing
/// direct {@code CopilotClient} usage from summary generation.
///
/// Covers behaviors: OUT-04, OUT-05
public interface GenerateAiSummaryPort {

    /// Generate an AI-powered executive summary from the given prompt.
    ///
    /// @param prompt the summary prompt (already rendered with report content)
    /// @return the generated summary text, or empty if generation failed or is unavailable
    Optional<String> generate(String prompt);
}
