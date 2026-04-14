package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.ModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentMarkdownParser")
class AgentMarkdownParserTest {

    private final AgentMarkdownParser parser = new AgentMarkdownParser();

    @Nested
    @DisplayName("フロントマター付きパース")
    class WithFrontmatter {

        @Test
        @DisplayName("標準的なエージェント定義をパースできる")
        void parsesStandardAgentDefinition() {
            String content = """
                ---
                name: security
                description: "セキュリティレビュー"
                model: claude-sonnet-4
                ---
                
                # セキュリティレビューエージェント
                
                ## Role
                
                あなたはセキュリティ専門のレビュアーです。
                
                ## Instruction
                
                ${repository} をレビューしてください。
                
                ## Focus Areas
                
                - SQLインジェクション
                - XSS脆弱性
                
                ## Output Format
                
                レビュー結果を出力してください。
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "security.agent.md");

            assertThat(config.name()).isEqualTo("security");
            assertThat(config.displayName()).isEqualTo("セキュリティレビュー");
            assertThat(config.model()).isEqualTo("claude-sonnet-4");
            assertThat(config.focusAreas()).containsExactly("SQLインジェクション", "XSS脆弱性");
            assertThat(config.systemPrompt()).contains("セキュリティ専門");
            assertThat(config.instruction()).contains("${repository}");
            assertThat(config.outputFormat()).contains("レビュー結果");
        }

        @Test
        @DisplayName("nameがない場合はファイル名から導出される")
        void derivesNameFromFilename() {
            String content = """
                ---
                description: "テストエージェント"
                ---
                
                ## Role
                
                テスト用。
                
                ## Instruction
                
                ${repository} をレビューしてください。
                
                ## Focus Areas
                
                - テスト項目
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "my-agent.agent.md");

            assertThat(config.name()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("モデル未指定時はデフォルトモデルが使用される")
        void usesDefaultModelWhenNotSpecified() {
            String content = """
                ---
                name: test
                ---
                
                ## Role
                
                テスト用。
                
                ## Instruction
                
                ${repository} をレビューしてください。
                
                ## Focus Areas
                
                - 項目
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "test.agent.md");

            assertThat(config.model()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }
    }

    @Nested
    @DisplayName("フロントマターなしパース")
    class WithoutFrontmatter {

        @Test
        @DisplayName("フロントマターがない場合はnullを返す（ポリシー違反）")
        void rejectsContentWithoutFrontmatter() {
            String content = """
                # Agent
                
                あなたはレビュアーです。
                
                ## Instruction
                
                ${repository} をレビューしてください。
                
                ## Focus Areas
                
                - コードの可読性
                - 命名規則
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "simple.agent.md");

            // New policy: frontmatter is required, so content without it is rejected
            assertThat(config).isNull();
        }
    }

    @Nested
    @DisplayName("セクション抽出")
    class SectionExtraction {

        @Test
        @DisplayName("Focus Areas セクションが空の場合はデフォルト値が使用される")
        void usesDefaultFocusAreasWhenEmpty() {
            String content = """
                ---
                name: minimal
                ---
                
                ## Role
                
                レビュアー。
                
                ## Instruction
                
                ${repository} をレビュー。
                
                ## Focus Areas
                
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "minimal.agent.md");

            assertThat(config).isNotNull();
            assertThat(config.focusAreas()).isNotEmpty();
            assertThat(config.focusAreas()).contains("一般的なコード品質");
        }

        @Test
        @DisplayName("複数の認識済みセクションを正しく抽出する")
        void extractsMultipleSections() {
            String content = """
                ---
                name: multi
                ---
                
                ## Role
                
                ロール内容。
                
                ## Instruction
                
                指示内容 ${repository}。
                
                ## Output Format
                
                フォーマット。
                
                ## Focus Areas
                
                - 項目A
                - 項目B
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "multi.agent.md");

            assertThat(config).isNotNull();
            assertThat(config.systemPrompt()).contains("ロール内容");
            assertThat(config.instruction()).contains("指示内容");
            assertThat(config.outputFormat()).contains("フォーマット");
            assertThat(config.focusAreas()).containsExactly("項目A", "項目B");
        }
    }

    @Nested
    @DisplayName("ファイル名からの名前抽出")
    class FilenameExtraction {

        @Test
        @DisplayName(".agent.md拡張子を除去する")
        void removesAgentMdExtension() {
            String content = """
                ---
                description: test
                ---
                
                ## Role
                test
                
                ## Instruction
                
                ${repository} を確認。
                
                ## Focus Areas
                - item
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "code-quality.agent.md");

            assertThat(config).isNotNull();
            assertThat(config.name()).isEqualTo("code-quality");
        }

        @Test
        @DisplayName(".md拡張子を除去する")
        void removesMdExtension() {
            String content = """
                ---
                description: test
                ---
                
                ## Role
                test
                
                ## Instruction
                
                ${repository} を確認。
                
                ## Focus Areas
                - item
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "reviewer.md");

            assertThat(config).isNotNull();
            assertThat(config.name()).isEqualTo("reviewer");
        }
    }

    @Nested
    @DisplayName("セキュリティポリシー統合")
    class SecurityPolicyIntegration {

        @Test
        @DisplayName("enabled: falseのエージェントはnullを返す")
        void disabledAgentReturnsNull() {
            String content = """
                ---
                name: disabled-agent
                enabled: false
                ---
                
                ## Role
                テスト
                
                ## Instruction
                ${repository} レビュー
                
                ## Focus Areas
                - item
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "disabled-agent.agent.md");
            assertThat(config).isNull();
        }

        @Test
        @DisplayName("不正なモデル名を持つエージェントはnullを返す")
        void invalidModelReturnsNull() {
            String content = """
                ---
                name: bad-model
                model: evil-hacked-model
                ---
                
                ## Role
                テスト
                
                ## Instruction
                ${repository} レビュー
                
                ## Focus Areas
                - item
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "bad-model.agent.md");
            assertThat(config).isNull();
        }

        @Test
        @DisplayName("大文字を含む名前はポリシー違反でnullを返す")
        void uppercaseNameReturnsNull() {
            String content = """
                ---
                name: BadName
                ---
                
                ## Role
                テスト
                
                ## Instruction
                ${repository} レビュー
                
                ## Focus Areas
                - item
                """;

            AgentConfig config = parser.parseContent(content.stripIndent(), "BadName.agent.md");
            assertThat(config).isNull();
        }

        @Test
        @DisplayName("parseContentSafeは詳細なリジェクト理由を返す")
        void parseContentSafeProvidesRejectionReason() {
            String content = """
                ---
                name: test
                model: evil-model
                ---
                
                ## Role
                テスト
                
                ## Instruction
                ${repository} レビュー
                
                ## Focus Areas
                - item
                """;

            var result = parser.parseContentSafe(content.stripIndent(), "test.agent.md");
            assertThat(result.accepted()).isFalse();
            assertThat(result.rejectionReason()).contains("not in the allowed model list");
        }
    }
}
