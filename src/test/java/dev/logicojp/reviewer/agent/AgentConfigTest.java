package dev.logicojp.reviewer.agent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;


@DisplayName("AgentConfig")
class AgentConfigTest {

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("nameがnullの場合は空文字列に設定される")
        void nullNameDefaultsToEmpty() {
            var config = AgentConfig.builder()
                .systemPrompt("prompt")
                .instruction("inst")
                .build();
            Assertions.assertThat(config.name()).isEmpty();
        }

        @Test
        @DisplayName("displayNameがnullの場合はnameと同じ値になる")
        void nullDisplayNameDefaultsToName() {
            var config = AgentConfig.builder()
                .name("test-agent")
                .build();
            Assertions.assertThat(config.displayName()).isEqualTo("test-agent");
        }

        @Test
        @DisplayName("modelがnullの場合はデフォルトモデルが使用される")
        void nullModelUsesDefault() {
            var config = AgentConfig.builder().build();
            Assertions.assertThat(config.model()).isNotBlank();
        }

        @Test
        @DisplayName("focusAreasがnullの場合は空リストになる")
        void nullFocusAreasDefaultsToEmpty() {
            var config = AgentConfig.builder().build();
            Assertions.assertThat(config.focusAreas()).isEmpty();
        }

        @Test
        @DisplayName("skillsがnullの場合は空リストになる")
        void nullSkillsDefaultsToEmpty() {
            var config = AgentConfig.builder().build();
            Assertions.assertThat(config.skills()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("全フィールドを設定して構築できる")
        void fullBuild() {
            var config = AgentConfig.builder()
                .name("security")
                .displayName("Security Reviewer")
                .model("gpt-4o")
                .systemPrompt("You are a security expert.")
                .instruction("Review ${repository}")
                .outputFormat("## Output Format\n\nReport findings")
                .focusAreas(List.of("SQL Injection", "XSS"))
                .skills(List.of())
                .build();

            Assertions.assertThat(config.name()).isEqualTo("security");
            Assertions.assertThat(config.displayName()).isEqualTo("Security Reviewer");
            Assertions.assertThat(config.model()).isEqualTo("gpt-4o");
            Assertions.assertThat(config.focusAreas()).containsExactly("SQL Injection", "XSS");
        }

        @Test
        @DisplayName("from()でコピーを作成できる")
        void fromCopy() {
            var original = AgentConfig.builder()
                .name("original")
                .displayName("Original Agent")
                .model("gpt-4o")
                .systemPrompt("sys")
                .instruction("inst")
                .build();

            var copy = AgentConfig.Builder.from(original).name("copy").build();

            Assertions.assertThat(copy.name()).isEqualTo("copy");
            Assertions.assertThat(copy.displayName()).isEqualTo("Original Agent");
            Assertions.assertThat(copy.model()).isEqualTo("gpt-4o");
        }
    }

    @Nested
    @DisplayName("withModel")
    class WithModel {

        @Test
        @DisplayName("モデルを上書きした新しいインスタンスを返す")
        void overridesModel() {
            var config = AgentConfig.builder()
                .name("agent")
                .model("old-model")
                .build();

            var updated = config.withModel("new-model");

            Assertions.assertThat(updated.model()).isEqualTo("new-model");
            Assertions.assertThat(updated.name()).isEqualTo("agent");
            Assertions.assertThat(config.model()).isEqualTo("old-model"); // original unchanged
        }
    }

    @Nested
    @DisplayName("validateRequired")
    class ValidateRequired {

        @Test
        @DisplayName("必須フィールドが揃っていれば例外なし")
        void validConfig() {
            var config = AgentConfig.builder()
                .name("agent")
                .systemPrompt("You are an expert.")
                .instruction("Review the code.")
                .focusAreas(List.of("Quality"))
                .build();
            config.validateRequired(); // no exception
        }

        @Test
        @DisplayName("nameが空の場合はIllegalArgumentExceptionをスローする")
        void throwsOnEmptyName() {
            var config = AgentConfig.builder()
                .systemPrompt("sys")
                .instruction("inst")
                .build();
            Assertions.assertThatThrownBy(config::validateRequired)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        }

        @Test
        @DisplayName("systemPromptが空の場合はIllegalArgumentExceptionをスローする")
        void throwsOnEmptySystemPrompt() {
            var config = AgentConfig.builder()
                .name("agent")
                .instruction("inst")
                .build();
            Assertions.assertThatThrownBy(config::validateRequired)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemPrompt");
        }
    }

    @Nested
    @DisplayName("normalizeOutputFormat")
    class NormalizeOutputFormat {

        @Test
        @DisplayName("nullの場合はnullのまま")
        void nullRemainsNull() {
            var config = AgentConfig.builder().outputFormat(null).build();
            Assertions.assertThat(config.outputFormat()).isNull();
        }

        @Test
        @DisplayName("##で始まるフォーマットはそのまま保持される")
        void preservesHeaderFormat() {
            var config = AgentConfig.builder()
                .outputFormat("## Output Format\n\nDetails")
                .build();
            Assertions.assertThat(config.outputFormat()).startsWith("## Output Format");
        }

        @Test
        @DisplayName("##で始まらないフォーマットにはヘッダが追加される")
        void addsHeaderIfMissing() {
            var config = AgentConfig.builder()
                .outputFormat("Report findings as a list.")
                .build();
            Assertions.assertThat(config.outputFormat()).startsWith("## Output Format");
            Assertions.assertThat(config.outputFormat()).contains("Report findings as a list.");
        }
    }
}
