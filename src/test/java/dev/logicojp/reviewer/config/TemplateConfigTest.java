package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemplateConfig")
class TemplateConfigTest {

    @Test
    @DisplayName("null値はデフォルトテンプレートに補完される")
    void nullValuesAreDefaulted() {
        TemplateConfig config = new TemplateConfig(
            null, null, null, null, null, null, null, null, null);

        assertThat(config.directory()).isEqualTo("templates");
        assertThat(config.defaultOutputFormat()).isEqualTo("default-output-format.md");
        assertThat(config.report()).isEqualTo("report.md");
        assertThat(config.localReviewContent()).isEqualTo("local-review-content.md");
        assertThat(config.outputConstraints()).isEqualTo("output-constraints.md");
        assertThat(config.reviewQualityConstraints()).isEqualTo("review-quality-constraints.md");
        assertThat(config.reportLinkEntry()).isEqualTo("report-link-entry.md");

        assertThat(config.summary().systemPrompt()).isEqualTo("summary-system.md");
        assertThat(config.summary().userPrompt()).isEqualTo("summary-prompt.md");
        assertThat(config.summary().executiveSummary()).isEqualTo("executive-summary.md");
        assertThat(config.summary().resultEntry()).isEqualTo("summary-result-entry.md");
        assertThat(config.summary().resultErrorEntry()).isEqualTo("summary-result-error-entry.md");

        assertThat(config.fallback().summary()).isEqualTo("fallback-summary.md");
        assertThat(config.fallback().agentRow()).isEqualTo("fallback-agent-row.md");
        assertThat(config.fallback().agentSuccess()).isEqualTo("fallback-agent-success.md");
        assertThat(config.fallback().agentFailure()).isEqualTo("fallback-agent-failure.md");
    }

    @Test
    @DisplayName("空白値はデフォルトに置換される")
    void blankValuesAreDefaulted() {
        TemplateConfig config = new TemplateConfig(
            " ", " ", "\t", "\n", " ", " ", " ",
            new TemplateConfig.SummaryTemplates(" ", "\t", "\n", " ", " "),
            new TemplateConfig.FallbackTemplates(" ", " ", " ", " ")
        );

        assertThat(config.directory()).isEqualTo("templates");
        assertThat(config.reviewQualityConstraints()).isEqualTo("review-quality-constraints.md");
        assertThat(config.summary().systemPrompt()).isEqualTo("summary-system.md");
        assertThat(config.fallback().summary()).isEqualTo("fallback-summary.md");
    }
}
