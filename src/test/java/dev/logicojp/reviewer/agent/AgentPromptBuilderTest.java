package dev.logicojp.reviewer.agent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;


@DisplayName("AgentPromptBuilder")
class AgentPromptBuilderTest {

    @Nested
    @DisplayName("buildFullSystemPrompt")
    class BuildFullSystemPrompt {

        @Test
        @DisplayName("systemPrompt、focusAreas、outputFormatを結合する")
        void combinesAllSections() {
            var config = AgentConfig.builder()
                .name("agent")
                .systemPrompt("You are an expert reviewer.")
                .focusAreas(List.of("Security", "Performance"))
                .outputFormat("## Output Format\n\nReport in table format.")
                .build();

            String result = AgentPromptBuilder.buildFullSystemPrompt(config);

            Assertions.assertThat(result).contains("expert reviewer");
            Assertions.assertThat(result).contains("## Focus Areas");
            Assertions.assertThat(result).contains("- Security");
            Assertions.assertThat(result).contains("- Performance");
            Assertions.assertThat(result).contains("Report in table format.");
        }

        @Test
        @DisplayName("focusAreasが空の場合はFocus Areasセクションを省略する")
        void omitsFocusAreasWhenEmpty() {
            var config = AgentConfig.builder()
                .name("agent")
                .systemPrompt("You are an expert.")
                .build();

            String result = AgentPromptBuilder.buildFullSystemPrompt(config);
            Assertions.assertThat(result).doesNotContain("## Focus Areas");
        }

        @Test
        @DisplayName("カスタムfocusAreasガイダンスを使用できる")
        void customGuidance() {
            var config = AgentConfig.builder()
                .name("agent")
                .systemPrompt("sys")
                .focusAreas(List.of("Area"))
                .build();

            String result = AgentPromptBuilder.buildFullSystemPrompt(config, "Custom guidance text.");
            Assertions.assertThat(result).contains("Custom guidance text.");
        }
    }

    @Nested
    @DisplayName("buildInstruction")
    class BuildInstruction {

        @Test
        @DisplayName("プレースホルダを置換する")
        void replacesPlaceholders() {
            var config = AgentConfig.builder()
                .name("security")
                .displayName("Security Agent")
                .instruction("Review ${repository} as ${displayName} (${name})")
                .focusAreas(List.of("SQL Injection"))
                .build();

            String result = AgentPromptBuilder.buildInstruction(config, "owner/repo");

            Assertions.assertThat(result).contains("owner/repo");
            Assertions.assertThat(result).contains("Security Agent");
            Assertions.assertThat(result).contains("security");
        }

        @Test
        @DisplayName("instructionが空の場合はIllegalStateExceptionをスローする")
        void throwsOnMissingInstruction() {
            var config = AgentConfig.builder().name("agent").build();
            Assertions.assertThatThrownBy(() -> AgentPromptBuilder.buildInstruction(config, "repo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
        }
    }

    @Nested
    @DisplayName("buildLocalInstruction")
    class BuildLocalInstruction {

        @Test
        @DisplayName("ソースコードをタグで囲んで挿入する")
        void wrapsSourceCode() {
            var config = AgentConfig.builder()
                .name("agent")
                .displayName("Agent")
                .instruction("Review ${repository}")
                .build();

            String result = AgentPromptBuilder.buildLocalInstruction(
                config, "my-project", "public class Main {}");

            Assertions.assertThat(result).contains("<source_code");
            Assertions.assertThat(result).contains("public class Main {}");
            Assertions.assertThat(result).contains("</source_code>");
            Assertions.assertThat(result).contains("trust_level=\"untrusted\"");
        }

        @Test
        @DisplayName("カスタムヘッダを使用できる")
        void customHeader() {
            var config = AgentConfig.builder()
                .name("agent")
                .instruction("Review ${repository}")
                .build();

            String result = AgentPromptBuilder.buildLocalInstruction(
                config, "project", "code", "Custom header text.");

            Assertions.assertThat(result).contains("Custom header text.");
        }
    }
}
