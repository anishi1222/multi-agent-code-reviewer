package dev.logicojp.reviewer.domain.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentSanitizationPipeline")
class ContentSanitizationPipelineTest {

    @Test
    @DisplayName("定義順にルールを適用する")
    void appliesRulesInOrder() {
        var pipeline = new ContentSanitizationPipeline(List.of(
            new ContentSanitizationRule(Pattern.compile("abc"), "x"),
            new ContentSanitizationRule(Pattern.compile("x"), "y")
        ));

        String result = pipeline.apply("abc");

        assertThat(result).isEqualTo("y");
    }

    @Test
    @DisplayName("複数ルールを順次適用して最終文字列を返す")
    void appliesAllRulesSequentially() {
        var pipeline = new ContentSanitizationPipeline(List.of(
            new ContentSanitizationRule(Pattern.compile("<thinking>.*?</thinking>", Pattern.DOTALL), ""),
            new ContentSanitizationRule(Pattern.compile("\\n{3,}"), "\n\n")
        ));

        String result = pipeline.apply("A\n<thinking>x</thinking>\n\n\nB");

        assertThat(result).isEqualTo("A\n\nB");
    }
}