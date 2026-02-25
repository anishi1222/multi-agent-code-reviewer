package dev.logicojp.reviewer.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("TemplateConfig")
class TemplateConfigTest {

    @Test
    @DisplayName("null値はデフォルトテンプレートに補完される")
    void nullValuesAreDefaulted() {
        var config = new TemplateConfig(null, null, null, null, null, null, null, null);

        Assertions.assertThat(config.directory()).isEqualTo("templates");
        Assertions.assertThat(config.defaultOutputFormat()).isEqualTo("default-output-format.md");
        Assertions.assertThat(config.report()).isEqualTo("report.md");
        Assertions.assertThat(config.localReviewContent()).isEqualTo("local-review-content.md");
        Assertions.assertThat(config.outputConstraints()).isEqualTo("output-constraints.md");
        Assertions.assertThat(config.reportLinkEntry()).isEqualTo("report-link-entry.md");

        Assertions.assertThat(config.summary().systemPrompt()).isEqualTo("summary-system.md");
        Assertions.assertThat(config.summary().userPrompt()).isEqualTo("summary-prompt.md");
        Assertions.assertThat(config.summary().executiveSummary()).isEqualTo("executive-summary.md");
        Assertions.assertThat(config.summary().resultEntry()).isEqualTo("summary-result-entry.md");
        Assertions.assertThat(config.summary().resultErrorEntry()).isEqualTo("summary-result-error-entry.md");

        Assertions.assertThat(config.fallback().summary()).isEqualTo("fallback-summary.md");
        Assertions.assertThat(config.fallback().agentRow()).isEqualTo("fallback-agent-row.md");
        Assertions.assertThat(config.fallback().agentSuccess()).isEqualTo("fallback-agent-success.md");
        Assertions.assertThat(config.fallback().agentFailure()).isEqualTo("fallback-agent-failure.md");
    }

    @Test
    @DisplayName("空白値はデフォルトに置換される")
    void blankValuesAreDefaulted() {
        var config = new TemplateConfig(
            " ", " ", "\t", "\n", " ", " ",
            new TemplateConfig.SummaryTemplates(" ", "\t", "\n", " ", " "),
            new TemplateConfig.FallbackTemplates(" ", " ", " ", " ")
        );

        Assertions.assertThat(config.directory()).isEqualTo("templates");
        Assertions.assertThat(config.summary().systemPrompt()).isEqualTo("summary-system.md");
        Assertions.assertThat(config.fallback().summary()).isEqualTo("fallback-summary.md");
    }
}
