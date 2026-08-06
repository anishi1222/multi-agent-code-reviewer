package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AgentConfig")
class AgentConfigTest {

    private static final String VALID_NAME = "test-agent";
    private static final String VALID_SYSTEM_PROMPT = "You are a helpful reviewer.";
    private static final String VALID_INSTRUCTION = "Review ${repository} for ${displayName}";
    private static final String VALID_OUTPUT_FORMAT = """
        ## Output
        | 項目 | 内容 |
        | **Priority** | Critical |
        | **指摘の概要** | Summary |
        | **推奨対応** | Fix |
        | **効果** | Improve |
        """;

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("nameがnullの場合は空文字列に設定される")
        void nullNameDefaultsToEmptyString() {
            AgentConfig config = new AgentConfig(
                null, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            assertThat(config.name()).isEmpty();
        }

        @Test
        @DisplayName("displayNameがnullの場合はnameが使用される")
        void nullDisplayNameDefaultsToName() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, null, "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            assertThat(config.displayName()).isEqualTo(VALID_NAME);
        }

        @Test
        @DisplayName("displayNameが空白の場合はnameが使用される")
        void blankDisplayNameDefaultsToName() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "  ", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            assertThat(config.displayName()).isEqualTo(VALID_NAME);
        }

        @Test
        @DisplayName("modelがnullの場合はデフォルトモデルが使用される")
        void nullModelDefaultsToClaudeSonnet() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", null, VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            assertThat(config.model()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("modelが空白の場合はデフォルトモデルが使用される")
        void blankModelDefaultsToClaudeSonnet() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "  ", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            assertThat(config.model()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("focusAreasがnullの場合は空リストになる")
        void nullFocusAreasDefaultsToEmptyList() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, null, List.of()
            );
            assertThat(config.focusAreas()).isEmpty();
        }

        @Test
        @DisplayName("skillsがnullの場合は空リストになる")
        void nullSkillsDefaultsToEmptyList() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), null
            );
            assertThat(config.skills()).isEmpty();
        }
    }

    @Nested
    @DisplayName("outputFormat正規化")
    class OutputFormatNormalization {

        @Test
        @DisplayName("outputFormatがnullの場合はnullが設定される（外部テンプレートから読み込まれる）")
        void nullOutputFormatGetsNull() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                null, List.of("area"), List.of()
            );
            assertThat(config.outputFormat()).isNull();
        }

        @Test
        @DisplayName("outputFormatが空白の場合はnullが設定される（外部テンプレートから読み込まれる）")
        void blankOutputFormatGetsNull() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                "  ", List.of("area"), List.of()
            );
            assertThat(config.outputFormat()).isNull();
        }

        @Test
        @DisplayName("##で始まるoutputFormatはそのまま保持される")
        void outputFormatStartingWithHeadingIsPreserved() {
            String format = "## Custom Format\nContent here";
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                format, List.of("area"), List.of()
            );
            assertThat(config.outputFormat()).isEqualTo(format);
        }

        @Test
        @DisplayName("##で始まらないoutputFormatにはヘッダーが追加される")
        void outputFormatWithoutHeadingGetsHeader() {
            String format = "Custom content without heading";
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                format, List.of("area"), List.of()
            );
            assertThat(config.outputFormat()).startsWith("## Output Format");
            assertThat(config.outputFormat()).contains(format);
        }
    }

    @Nested
    @DisplayName("validateRequired")
    class ValidateRequired {

        @Test
        @DisplayName("必須フィールドが全て設定されている場合は例外を投げない")
        void validConfigDoesNotThrow() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            config.validateRequired(); // Should not throw
        }

        @Test
        @DisplayName("nameが空の場合は例外を投げる")
        void emptyNameThrows() {
            AgentConfig config = new AgentConfig(
                "", "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            
            assertThatThrownBy(config::validateRequired)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        }

        @Test
        @DisplayName("systemPromptが空の場合は例外を投げる")
        void emptySystemPromptThrows() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", "", VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            
            assertThatThrownBy(config::validateRequired)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemPrompt");
        }

        @Test
        @DisplayName("instructionが空の場合は例外を投げる")
        void emptyInstructionThrows() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, "",
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            
            assertThatThrownBy(config::validateRequired)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instruction");
        }

        @Test
        @DisplayName("複数の必須フィールドが欠けている場合は全てがメッセージに含まれる")
        void multipleMissingFieldsAllReported() {
            AgentConfig config = new AgentConfig(
                "", "Display", "model", "", "",
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            
            assertThatThrownBy(config::validateRequired)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("systemPrompt")
                .hasMessageContaining("instruction");
        }
    }

    @Nested
    @DisplayName("withModel")
    class WithModel {

        @Test
        @DisplayName("新しいモデルでコピーを作成する")
        void createsNewInstanceWithDifferentModel() {
            AgentConfig original = new AgentConfig(
                VALID_NAME, "Display", "original-model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area1", "area2"), List.of()
            );
            
            AgentConfig updated = original.withModel("new-model");
            
            assertThat(updated.model()).isEqualTo("new-model");
            assertThat(updated.name()).isEqualTo(original.name());
            assertThat(updated.displayName()).isEqualTo(original.displayName());
            assertThat(updated.systemPrompt()).isEqualTo(original.systemPrompt());
            assertThat(updated.focusAreas()).isEqualTo(original.focusAreas());
        }

        @Test
        @DisplayName("元のインスタンスは変更されない")
        void originalInstanceUnchanged() {
            AgentConfig original = new AgentConfig(
                VALID_NAME, "Display", "original-model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            
            original.withModel("new-model");
            
            assertThat(original.model()).isEqualTo("original-model");
        }

        @Test
        @DisplayName("withModelはskillsやfocusAreasを保持する")
        void withModelPreservesOtherFields() {
            SkillDefinition skill = SkillDefinition.of("skill-1", "Skill", "desc", "prompt");
            AgentConfig original = new AgentConfig(
                VALID_NAME, "Display", "original-model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area1", "area2"), List.of(skill)
            );

            AgentConfig updated = original.withModel("new-model");

            assertThat(updated.skills()).containsExactly(skill);
            assertThat(updated.focusAreas()).containsExactly("area1", "area2");
            assertThat(updated.instruction()).isEqualTo(original.instruction());
            assertThat(updated.systemPrompt()).isEqualTo(original.systemPrompt());
        }
    }

    @Nested
    @DisplayName("buildFullSystemPrompt")
    class BuildFullSystemPrompt {

        @Test
        @DisplayName("systemPromptを含む")
        void includesSystemPrompt() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildFullSystemPrompt(config);
            
            assertThat(result).contains(VALID_SYSTEM_PROMPT);
        }

        @Test
        @DisplayName("focusAreasをリスト形式で含む")
        void includesFocusAreasAsList() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("Security", "Performance"), List.of()
            );
            
            String result = AgentPromptBuilder.buildFullSystemPrompt(config);
            
            assertThat(result).contains("## Focus Areas");
            assertThat(result).contains("- Security");
            assertThat(result).contains("- Performance");
        }

        @Test
        @DisplayName("outputFormatを含む")
        void includesOutputFormat() {
            String customFormat = "## My Format\nCustom content";
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                customFormat, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildFullSystemPrompt(config);
            
            assertThat(result).contains(customFormat);
        }

        @Test
        @DisplayName("空のfocusAreasの場合はFocus Areasセクションを含まない")
        void emptyFocusAreasOmitsSection() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildFullSystemPrompt(config);
            
            assertThat(result).doesNotContain("## Focus Areas");
        }
    }

    @Nested
    @DisplayName("buildInstruction")
    class BuildInstruction {

        @Test
        @DisplayName("${repository}プレースホルダを置換する")
        void replacesRepositoryPlaceholder() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display Name", "model", VALID_SYSTEM_PROMPT, 
                "Review ${repository}",
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildInstruction(config, "my-org/my-repo");
            
            assertThat(result).contains("my-org/my-repo");
            assertThat(result).doesNotContain("${repository}");
        }

        @Test
        @DisplayName("${displayName}プレースホルダを置換する")
        void replacesDisplayNamePlaceholder() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Security Reviewer", "model", VALID_SYSTEM_PROMPT, 
                "As ${displayName}, review this",
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildInstruction(config, "repo");
            
            assertThat(result).contains("Security Reviewer");
            assertThat(result).doesNotContain("${displayName}");
        }

        @Test
        @DisplayName("${name}プレースホルダを置換する")
        void replacesNamePlaceholder() {
            AgentConfig config = new AgentConfig(
                "agent-id", "Display", "model", VALID_SYSTEM_PROMPT, 
                "Agent: ${name}",
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildInstruction(config, "repo");
            
            assertThat(result).contains("agent-id");
            assertThat(result).doesNotContain("${name}");
        }

        @Test
        @DisplayName("${focusAreas}プレースホルダを置換する")
        void replacesFocusAreasPlaceholder() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, 
                "Focus on:\n${focusAreas}",
                VALID_OUTPUT_FORMAT, List.of("SQL Injection", "XSS"), List.of()
            );
            
            String result = AgentPromptBuilder.buildInstruction(config, "repo");
            
            assertThat(result).contains("- SQL Injection");
            assertThat(result).contains("- XSS");
            assertThat(result).doesNotContain("${focusAreas}");
        }

        @Test
        @DisplayName("instructionが設定されていない場合は例外を投げる")
        void throwsWhenInstructionNotConfigured() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, null,
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            assertThatThrownBy(() -> AgentPromptBuilder.buildInstruction(config, "repo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VALID_NAME);
        }
    }

    @Nested
    @DisplayName("buildLocalInstruction")
    class BuildLocalInstruction {

        @Test
        @DisplayName("ソースコードコンテンツを埋め込む")
        void embedsSourceContent() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String sourceCode = "public class Test {}";
            String result = AgentPromptBuilder.buildLocalInstruction(config, "MyProject", sourceCode);
            
            assertThat(result).contains(sourceCode);
            assertThat(result).contains("以下は対象ディレクトリのソースコードです");
        }

        @Test
        @DisplayName("プレースホルダも置換される")
        void replacesPlaceholders() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Code Reviewer", "model", VALID_SYSTEM_PROMPT, 
                "Review ${repository} as ${displayName}",
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = AgentPromptBuilder.buildLocalInstruction(config, "LocalDir", "code");
            
            assertThat(result).contains("LocalDir");
            assertThat(result).contains("Code Reviewer");
        }
    }

    @Nested
    @DisplayName("アクセサメソッド")
    class Accessors {

        @Test
        @DisplayName("getter系メソッドは正しい値を返す")
        void gettersReturnCorrectValues() {
            SkillDefinition skill = SkillDefinition.of("id", "name", "desc", "prompt");
            AgentConfig config = new AgentConfig(
                "my-name", "My Display", "my-model", "system", "review",
                "## format", List.of("area"), List.of(skill)
            );
            
            assertThat(config.name()).isEqualTo("my-name");
            assertThat(config.displayName()).isEqualTo("My Display");
            assertThat(config.model()).isEqualTo("my-model");
            assertThat(config.systemPrompt()).isEqualTo("system");
            assertThat(config.instruction()).isEqualTo("review");
            assertThat(config.outputFormat()).isEqualTo("## format");
            assertThat(config.focusAreas()).containsExactly("area");
            assertThat(config.skills()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("nameとdisplayNameを含む")
        void containsNameAndDisplayName() {
            AgentConfig config = new AgentConfig(
                "agent-id", "Agent Display Name", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of(), List.of()
            );
            
            String result = config.toString();
            
            assertThat(result).contains("agent-id");
            assertThat(result).contains("Agent Display Name");
        }
    }

    @Nested
    @DisplayName("不変性")
    class Immutability {

        @Test
        @DisplayName("focusAreasは不変リストである")
        void focusAreasIsImmutable() {
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of("area"), List.of()
            );
            
            assertThatThrownBy(() -> config.focusAreas().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("skillsは不変リストである")
        void skillsIsImmutable() {
            SkillDefinition skill = SkillDefinition.of("id", "name", "desc", "prompt");
            AgentConfig config = new AgentConfig(
                VALID_NAME, "Display", "model", VALID_SYSTEM_PROMPT, VALID_INSTRUCTION,
                VALID_OUTPUT_FORMAT, List.of(), List.of(skill)
            );
            
            assertThatThrownBy(() -> config.skills().add(skill))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
