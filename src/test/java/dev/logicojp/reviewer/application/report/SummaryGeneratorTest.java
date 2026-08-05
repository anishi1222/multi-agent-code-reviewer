package dev.logicojp.reviewer.application.report;

import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.report.FindingsExtractor;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryGenerator")
class SummaryGeneratorTest {

    // removed: timeout helpers moved out of SummaryGenerator during application-layer rewrite

    @Test
    @DisplayName("AI要約ビルダーの結果でサマリー本文を生成できる")
    void generatesSummaryUsingInjectedAiBuilder() {
        SummaryGenerator generator = new SummaryGenerator(
            new InMemoryTemplates(),
            _ -> Optional.of("AI summary content"),
            new SummaryGenerator.SummaryGenerationConfig(
                10_000,
                20_000,
                200,
                1_000,
                100,
                2
            )
        );

        List<ReviewResult> results = List.of(
            ReviewResult.builder()
                .agentConfig(new AgentConfig("security", "Security", "model", "system", "instruction", null, List.of(), List.of()))
                .repository("owner/repo")
                .content("### 1. 指摘\n| Priority | High |")
                .success(true)
                .build()
        );

        String summaryContent = generator.buildSummaryContent(results, "owner/repo");
        String formatted = generator.formatSummary(summaryContent, results, "owner/repo", "2026-02-19-20-30-44");

        assertThat(summaryContent).isEqualTo("AI summary content");
        assertThat(formatted).contains("AI summary content");
        assertThat(formatted).contains("owner/repo");
        assertThat(formatted).contains("Security");
        assertThat(FindingsExtractor.extractAll(results)).isNotEmpty();
    }

    private static final class InMemoryTemplates implements LoadTemplatePort {
        private final Map<String, String> templates = Map.of(
            "summary-prompt.md", "Repository: ${repository}\n${results}",
            "summary-result-entry.md", "${displayName}: ${content}\n",
            "summary-result-error-entry.md", "${displayName}: ${errorMessage}\n",
            "fallback-summary.md", "${tableRows}\n${agentSummaries}",
            "fallback-agent-row.md", "| ${displayName} | ${content} |\n",
            "fallback-agent-success.md", "${displayName}: ${content}\n",
            "fallback-agent-failure.md", "${displayName}: ${errorMessage}\n",
            "executive-summary.md", "# Executive Summary\n\n- Date: ${date}\n- Repository: ${repository}\n\n${summaryContent}\n\n${findingsSummary}\n\n${reportLinks}",
            "report-link-entry.md", "- [${displayName}](${filename})\n"
        );

        @Override
        public String render(String templateKey, Map<String, String> placeholders) {
            String rendered = loadRaw(templateKey);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            return rendered;
        }

        @Override
        public String loadRaw(String templateKey) {
            return templates.get(templateKey);
        }
    }
}
