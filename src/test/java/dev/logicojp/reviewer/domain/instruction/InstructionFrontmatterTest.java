package dev.logicojp.reviewer.domain.instruction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InstructionFrontmatter")
class InstructionFrontmatterTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("フロントマター付きコンテンツをパースする")
        void parsesFrontmatter() {
            String content = """
                ---
                applyTo: '**/*.java'
                description: Java rules
                ---
                Content body here.
                """;

            InstructionFrontmatter.Parsed result = InstructionFrontmatter.parse(content);

            assertThat(result.hasFrontmatter()).isTrue();
            assertThat(result.metadata()).containsEntry("applyTo", "**/*.java");
            assertThat(result.body()).contains("Content body here.");
        }

        @Test
        @DisplayName("フロントマターなしのコンテンツをパースする")
        void parsesWithoutFrontmatter() {
            String content = "Just plain content.";

            InstructionFrontmatter.Parsed result = InstructionFrontmatter.parse(content);

            assertThat(result.hasFrontmatter()).isFalse();
        }
    }

    @Nested
    @DisplayName("bodyOrRaw")
    class BodyOrRaw {

        @Test
        @DisplayName("フロントマターがある場合はbodyを返す")
        void returnsBodyWhenFrontmatterPresent() {
            String rawContent = """
                ---
                key: value
                ---
                Body content here.
                """;
            InstructionFrontmatter.Parsed parsed = InstructionFrontmatter.parse(rawContent);

            String result = InstructionFrontmatter.bodyOrRaw(parsed, rawContent);

            assertThat(result).isEqualTo("Body content here.");
        }

        @Test
        @DisplayName("フロントマターがない場合はrawContentを返す")
        void returnsRawWhenNoFrontmatter() {
            String rawContent = "Plain text content.";
            InstructionFrontmatter.Parsed parsed = InstructionFrontmatter.parse(rawContent);

            String result = InstructionFrontmatter.bodyOrRaw(parsed, rawContent);

            assertThat(result).isEqualTo(rawContent);
        }

        @Test
        @DisplayName("bodyが空の場合はrawContentを返す")
        void returnsRawWhenBodyEmpty() {
            String rawContent = """
                ---
                key: value
                ---
                """;
            InstructionFrontmatter.Parsed parsed = InstructionFrontmatter.parse(rawContent);

            String result = InstructionFrontmatter.bodyOrRaw(parsed, rawContent);

            assertThat(result).isEqualTo(rawContent);
        }
    }
}
