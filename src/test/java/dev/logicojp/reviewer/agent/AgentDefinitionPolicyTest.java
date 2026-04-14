package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentDefinitionPolicy")
class AgentDefinitionPolicyTest {

    // ── raw-content validation ──────────────────────────────────

    @Nested
    @DisplayName("validateRawContent")
    class ValidateRawContent {

        @Test
        @DisplayName("正常なフロントマター付きコンテンツは受け入れる")
        void acceptsValidContent() {
            String content = "---\nname: test\n---\n## Role\ntest";
            var result = AgentDefinitionPolicy.validateRawContent(content, "test.agent.md");
            assertThat(result.accepted()).isTrue();
        }

        @Test
        @DisplayName("nullコンテンツは拒否する")
        void rejectsNullContent() {
            var result = AgentDefinitionPolicy.validateRawContent(null, "test.agent.md");
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("empty");
        }

        @Test
        @DisplayName("空のコンテンツは拒否する")
        void rejectsBlankContent() {
            var result = AgentDefinitionPolicy.validateRawContent("   ", "test.agent.md");
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("empty");
        }

        @Test
        @DisplayName("最大サイズを超えるコンテンツは拒否する")
        void rejectsOversizedContent() {
            String content = "---\n" + "x".repeat(AgentDefinitionPolicy.MAX_AGENT_FILE_SIZE + 1);
            var result = AgentDefinitionPolicy.validateRawContent(content, "huge.agent.md");
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("exceeds maximum size");
        }

        @Test
        @DisplayName("フロントマターなしのコンテンツは拒否する")
        void rejectsWithoutFrontmatter() {
            String content = "# No Frontmatter\nJust text";
            var result = AgentDefinitionPolicy.validateRawContent(content, "no-fm.agent.md");
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("frontmatter");
        }
    }

    // ── name validation ─────────────────────────────────────────

    @Nested
    @DisplayName("validateName")
    class ValidateName {

        @Test
        @DisplayName("有効なエージェント名を受け入れる")
        void acceptsValidNames() {
            assertThat(AgentDefinitionPolicy.validateName("security").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateName("code-quality").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateName("waf-cost-optimization").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateName("a1-b2-c3").accepted()).isTrue();
        }

        @Test
        @DisplayName("空白の名前は拒否する")
        void rejectsBlankName() {
            assertThat(AgentDefinitionPolicy.validateName("").accepted()).isFalse();
            assertThat(AgentDefinitionPolicy.validateName(null).accepted()).isFalse();
        }

        @Test
        @DisplayName("大文字を含む名前は拒否する")
        void rejectsUppercaseName() {
            var result = AgentDefinitionPolicy.validateName("Security");
            assertThat(result.accepted()).isFalse();
        }

        @Test
        @DisplayName("パストラバーサルを含む名前は拒否する")
        void rejectsPathTraversalName() {
            var result = AgentDefinitionPolicy.validateName("../etc/passwd");
            assertThat(result.accepted()).isFalse();
        }

        @Test
        @DisplayName("特殊文字を含む名前は拒否する")
        void rejectsSpecialCharacters() {
            assertThat(AgentDefinitionPolicy.validateName("agent;drop").accepted()).isFalse();
            assertThat(AgentDefinitionPolicy.validateName("agent name").accepted()).isFalse();
            assertThat(AgentDefinitionPolicy.validateName("agent_name").accepted()).isFalse();
        }

        @Test
        @DisplayName("ハイフンで始まる名前は拒否する")
        void rejectsLeadingHyphen() {
            assertThat(AgentDefinitionPolicy.validateName("-agent").accepted()).isFalse();
        }

        @Test
        @DisplayName("長すぎる名前は拒否する")
        void rejectsTooLongName() {
            String longName = "a" + "-test".repeat(20);
            var result = AgentDefinitionPolicy.validateName(longName);
            assertThat(result.accepted()).isFalse();
        }
    }

    // ── model allowlist ─────────────────────────────────────────

    @Nested
    @DisplayName("validateModel")
    class ValidateModel {

        @Test
        @DisplayName("許可されたモデルプレフィックスを受け入れる")
        void acceptsAllowedModels() {
            assertThat(AgentDefinitionPolicy.validateModel("claude-sonnet-4", "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("claude-opus-4.6-1m", "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("gpt-4o", "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("o3", "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("o4-mini", "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("gemini-2.5-pro", "model").accepted()).isTrue();
        }

        @Test
        @DisplayName("許可されていないモデルは拒否する")
        void rejectsUnallowedModels() {
            var result = AgentDefinitionPolicy.validateModel("evil-model-v1", "model");
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("not in the allowed model list");
        }

        @Test
        @DisplayName("null/空のモデルは許可する（デフォルトが適用されるため）")
        void acceptsNullOrBlankModel() {
            assertThat(AgentDefinitionPolicy.validateModel(null, "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("", "model").accepted()).isTrue();
        }

        @Test
        @DisplayName("大文字小文字を区別しない")
        void caseInsensitive() {
            assertThat(AgentDefinitionPolicy.validateModel("Claude-Sonnet-4", "model").accepted()).isTrue();
            assertThat(AgentDefinitionPolicy.validateModel("GPT-4o", "model").accepted()).isTrue();
        }
    }

    // ── focus areas ─────────────────────────────────────────────

    @Nested
    @DisplayName("validateFocusAreas")
    class ValidateFocusAreas {

        @Test
        @DisplayName("通常のフォーカスエリアリストを受け入れる")
        void acceptsNormalList() {
            var result = AgentDefinitionPolicy.validateFocusAreas(
                List.of("SQLインジェクション", "XSS"));
            assertThat(result.accepted()).isTrue();
        }

        @Test
        @DisplayName("nullリストを受け入れる")
        void acceptsNull() {
            assertThat(AgentDefinitionPolicy.validateFocusAreas(null).accepted()).isTrue();
        }

        @Test
        @DisplayName("フォーカスエリアが多すぎる場合は拒否する")
        void rejectsTooMany() {
            List<String> tooMany = IntStream.range(0, AgentDefinitionPolicy.MAX_FOCUS_AREAS + 1)
                .mapToObj(i -> "item-" + i)
                .toList();
            var result = AgentDefinitionPolicy.validateFocusAreas(tooMany);
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("too many focus areas");
        }

        @Test
        @DisplayName("長すぎるフォーカスエリアテキストは拒否する")
        void rejectsTooLongItem() {
            String longItem = "x".repeat(AgentDefinitionPolicy.MAX_FOCUS_AREA_LENGTH + 1);
            var result = AgentDefinitionPolicy.validateFocusAreas(List.of(longItem));
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("exceeds maximum length");
        }
    }

    // ── parsed config validation ────────────────────────────────

    @Nested
    @DisplayName("validateParsed")
    class ValidateParsed {

        @Test
        @DisplayName("有効な設定を受け入れる")
        void acceptsValidConfig() {
            AgentConfig config = new AgentConfig(
                "security", "セキュリティ", "claude-sonnet-4",
                "system prompt", "instruction", "output format",
                List.of("SQLインジェクション"), List.of());
            var result = AgentDefinitionPolicy.validateParsed(config);
            assertThat(result.accepted()).isTrue();
        }

        @Test
        @DisplayName("不正なモデルを持つ設定は拒否する")
        void rejectsInvalidModel() {
            AgentConfig config = new AgentConfig(
                "test", "test", "evil-model",
                "system", "instruction", "output",
                List.of("item"), List.of());
            var result = AgentDefinitionPolicy.validateParsed(config);
            assertThat(result.accepted()).isFalse();
        }

        @Test
        @DisplayName("dialogue-roundsが範囲外の場合は拒否する")
        void rejectsOutOfRangeDialogueRounds() {
            AgentConfig config = AgentConfig.builder()
                .name("test")
                .model("claude-sonnet-4")
                .systemPrompt("system")
                .instruction("instruction")
                .focusAreas(List.of("item"))
                .dialogueRounds(99)
                .build();
            var result = AgentDefinitionPolicy.validateParsed(config);
            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("dialogue-rounds");
        }
    }

    // ── enabled flag ────────────────────────────────────────────

    @Nested
    @DisplayName("isAgentEnabled")
    class IsAgentEnabled {

        @Test
        @DisplayName("enabled未設定の場合はtrueを返す")
        void defaultEnabled() {
            assertThat(AgentDefinitionPolicy.isAgentEnabled(Map.of())).isTrue();
        }

        @Test
        @DisplayName("enabled: trueの場合はtrueを返す")
        void explicitlyEnabled() {
            assertThat(AgentDefinitionPolicy.isAgentEnabled(Map.of("enabled", "true"))).isTrue();
        }

        @Test
        @DisplayName("enabled: falseの場合はfalseを返す")
        void disabled() {
            assertThat(AgentDefinitionPolicy.isAgentEnabled(Map.of("enabled", "false"))).isFalse();
        }
    }

    // ── frontmatter key audit ───────────────────────────────────

    @Nested
    @DisplayName("auditFrontmatterKeys")
    class AuditFrontmatterKeys {

        @Test
        @DisplayName("認識済みキーのみの場合は警告なし（例外が発生しない）")
        void knownKeysDoNotThrow() {
            Map<String, String> metadata = Map.of("name", "test", "model", "claude-sonnet-4");
            // Should not throw
            AgentDefinitionPolicy.auditFrontmatterKeys(metadata, "test.agent.md");
        }

        @Test
        @DisplayName("未知のキーがあっても例外は発生しない（ログ警告のみ）")
        void unknownKeysDoNotThrow() {
            Map<String, String> metadata = Map.of(
                "name", "test",
                "unknown-key", "value",
                "another-unknown", "value2"
            );
            // Should not throw — just logs warnings
            AgentDefinitionPolicy.auditFrontmatterKeys(metadata, "test.agent.md");
        }
    }
}
