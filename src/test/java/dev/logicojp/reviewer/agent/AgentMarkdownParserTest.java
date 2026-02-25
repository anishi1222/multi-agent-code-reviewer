package dev.logicojp.reviewer.agent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


@DisplayName("AgentMarkdownParser")
class AgentMarkdownParserTest {

    private final AgentMarkdownParser parser = new AgentMarkdownParser();

    @Nested
    @DisplayName("parseContent")
    class ParseContent {

        @Test
        @DisplayName("フロントマター付きのエージェント定義をパースする")
        void parsesWithFrontmatter() {
            String content = """
                ---
                name: security-reviewer
                description: Security code review agent
                model: gpt-4o
                ---

                ## Role
                You are a security expert.

                ## Instruction
                Review ${repository} for vulnerabilities.

                ## Focus Areas
                - SQL Injection
                - XSS

                ## Output Format
                ### 指摘事項
                | Priority | 指摘の概要 | 推奨対応 | 効果 |
                """;

            AgentConfig config = parser.parseContent(content, "security.agent.md");

            Assertions.assertThat(config.name()).isEqualTo("security-reviewer");
            Assertions.assertThat(config.displayName()).isEqualTo("Security code review agent");
            Assertions.assertThat(config.model()).isEqualTo("gpt-4o");
            Assertions.assertThat(config.systemPrompt()).contains("security expert");
            Assertions.assertThat(config.instruction()).contains("${repository}");
            Assertions.assertThat(config.focusAreas()).contains("SQL Injection", "XSS");
        }

        @Test
        @DisplayName("フロントマターなしの場合はファイル名からnameを抽出する")
        void parsesWithoutFrontmatter() {
            String content = """
                ## Role
                You are a code reviewer.

                ## Instruction
                Review the code.

                ## Focus Areas
                - Quality
                """;

            AgentConfig config = parser.parseContent(content, "quality.agent.md");

            Assertions.assertThat(config.name()).isEqualTo("quality");
            Assertions.assertThat(config.systemPrompt()).contains("code reviewer");
        }
    }

    @Nested
    @DisplayName("extractNameFromFilename")
    class ExtractNameFromFilename {

        @Test
        @DisplayName(".agent.md拡張子を除去する")
        void removesAgentMdExtension() {
            Assertions.assertThat(AgentMarkdownParser.extractNameFromFilename("security.agent.md"))
                .isEqualTo("security");
        }

        @Test
        @DisplayName(".md拡張子を除去する")
        void removesMdExtension() {
            Assertions.assertThat(AgentMarkdownParser.extractNameFromFilename("review.md"))
                .isEqualTo("review");
        }

        @Test
        @DisplayName("拡張子なしの場合はそのまま返す")
        void noExtension() {
            Assertions.assertThat(AgentMarkdownParser.extractNameFromFilename("agent"))
                .isEqualTo("agent");
        }
    }

    @Nested
    @DisplayName("デフォルト出力フォーマット")
    class DefaultOutputFormat {

        @Test
        @DisplayName("デフォルト出力フォーマットが指定されている場合に使用される")
        void usesDefaultOutputFormat() {
            var parserWithDefault = new AgentMarkdownParser("## Default Output\n\nUse this format.");

            String content = """
                ---
                name: test-agent
                ---

                ## Role
                You are a reviewer.

                ## Instruction
                Review the code.

                ## Focus Areas
                - Quality
                """;

            AgentConfig config = parserWithDefault.parseContent(content, "test.agent.md");

            Assertions.assertThat(config.outputFormat()).contains("Default Output");
        }
    }
}
