package dev.logicojp.reviewer.domain.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillDefinition")
class SkillDefinitionTest {

    private static final String VALID_ID = "skill-001";
    private static final String VALID_NAME = "Test Skill";
    private static final String VALID_DESCRIPTION = "A test skill";
    private static final String VALID_PROMPT = "Execute the test skill with ${param}";

    @Nested
    @DisplayName("コンストラクタ - バリデーション")
    class ConstructorValidation {

        @Test
        @DisplayName("idがnullの場合は例外を投げる")
        void nullIdThrows() {
            assertThatThrownBy(() -> new SkillDefinition(
                null, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT, List.of(), Map.of()
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id");
        }

        @Test
        @DisplayName("idが空白の場合は例外を投げる")
        void blankIdThrows() {
            assertThatThrownBy(() -> new SkillDefinition(
                "  ", VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT, List.of(), Map.of()
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id");
        }

        @Test
        @DisplayName("promptがnullの場合は例外を投げる")
        void nullPromptThrows() {
            assertThatThrownBy(() -> new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, null, List.of(), Map.of()
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("prompt");
        }

        @Test
        @DisplayName("promptが空白の場合は例外を投げる")
        void blankPromptThrows() {
            assertThatThrownBy(() -> new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, "  ", List.of(), Map.of()
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("prompt");
        }
    }

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("nameがnullの場合はidが使用される")
        void nullNameDefaultsToId() {
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, null, VALID_DESCRIPTION, VALID_PROMPT, List.of(), Map.of()
            );
            assertThat(skill.name()).isEqualTo(VALID_ID);
        }

        @Test
        @DisplayName("nameが空白の場合はidが使用される")
        void blankNameDefaultsToId() {
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, "  ", VALID_DESCRIPTION, VALID_PROMPT, List.of(), Map.of()
            );
            assertThat(skill.name()).isEqualTo(VALID_ID);
        }

        @Test
        @DisplayName("descriptionがnullの場合は空文字列になる")
        void nullDescriptionDefaultsToEmpty() {
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, null, VALID_PROMPT, List.of(), Map.of()
            );
            assertThat(skill.description()).isEmpty();
        }

        @Test
        @DisplayName("parametersがnullの場合は空リストになる")
        void nullParametersDefaultsToEmptyList() {
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT, null, Map.of()
            );
            assertThat(skill.parameters()).isEmpty();
        }

        @Test
        @DisplayName("metadataがnullの場合は空マップになる")
        void nullMetadataDefaultsToEmptyMap() {
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT, List.of(), null
            );
            assertThat(skill.metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ファクトリメソッド - of")
    class OfFactory {

        @Test
        @DisplayName("最小限のフィールドでスキルを作成できる")
        void createsSkillWithMinimalFields() {
            SkillDefinition skill = SkillDefinition.of(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT
            );

            assertThat(skill.id()).isEqualTo(VALID_ID);
            assertThat(skill.name()).isEqualTo(VALID_NAME);
            assertThat(skill.description()).isEqualTo(VALID_DESCRIPTION);
            assertThat(skill.prompt()).isEqualTo(VALID_PROMPT);
            assertThat(skill.parameters()).isEmpty();
            assertThat(skill.metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildPrompt")
    class BuildPrompt {

        @Test
        @DisplayName("パラメータのプレースホルダを値で置換する")
        void replacesPlaceholders() {
            SkillParameter param = SkillParameter.required("language", "Target language");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION,
                "Analyze code in ${language}",
                List.of(param), Map.of()
            );

            String result = skill.buildPrompt(Map.of("language", "Java"), 10_000);

            assertThat(result).isEqualTo("Analyze code in Java");
        }

        @Test
        @DisplayName("複数のパラメータを置換する")
        void replacesMultiplePlaceholders() {
            SkillParameter param1 = SkillParameter.required("language", "Language");
            SkillParameter param2 = SkillParameter.required("scope", "Scope");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION,
                "Check ${language} code in ${scope}",
                List.of(param1, param2), Map.of()
            );

            String result = skill.buildPrompt(Map.of("language", "Python", "scope", "module"), 10_000);

            assertThat(result).isEqualTo("Check Python code in module");
        }

        @Test
        @DisplayName("値がない場合はデフォルト値を使用する")
        void usesDefaultValueWhenNotProvided() {
            SkillParameter param = SkillParameter.optional("format", "Output format", "JSON");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION,
                "Output in ${format}",
                List.of(param), Map.of()
            );

            String result = skill.buildPrompt(Map.of(), 10_000);

            assertThat(result).isEqualTo("Output in JSON");
        }

        @Test
        @DisplayName("提供された値がデフォルト値より優先される")
        void providedValueOverridesDefault() {
            SkillParameter param = SkillParameter.optional("format", "Format", "JSON");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION,
                "Output in ${format}",
                List.of(param), Map.of()
            );

            String result = skill.buildPrompt(Map.of("format", "XML"), 10_000);

            assertThat(result).isEqualTo("Output in XML");
        }

        @Test
        @DisplayName("パラメータがない場合はプロンプトをそのまま返す")
        void returnsPromptUnchangedWhenNoParameters() {
            SkillDefinition skill = SkillDefinition.of(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, "Simple prompt"
            );

            String result = skill.buildPrompt(Map.of(), 10_000);

            assertThat(result).isEqualTo("Simple prompt");
        }
    }

    @Nested
    @DisplayName("validateParameters")
    class ValidateParameters {

        @Test
        @DisplayName("必須パラメータがすべて提供されている場合は例外を投げない")
        void doesNotThrowWhenAllRequiredProvided() {
            SkillParameter required = SkillParameter.required("target", "Target");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT,
                List.of(required), Map.of()
            );

            skill.validateParameters(Map.of("target", "value"));
            // Should not throw
        }

        @Test
        @DisplayName("必須パラメータがない場合は例外を投げる")
        void throwsWhenRequiredParameterMissing() {
            SkillParameter required = SkillParameter.required("target", "Target");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT,
                List.of(required), Map.of()
            );

            assertThatThrownBy(() -> skill.validateParameters(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target");
        }

        @Test
        @DisplayName("オプショナルパラメータがなくても例外を投げない")
        void doesNotThrowWhenOptionalMissing() {
            SkillParameter optional = SkillParameter.optional("format", "Format", "default");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT,
                List.of(optional), Map.of()
            );

            skill.validateParameters(Map.of());
            // Should not throw
        }

        @Test
        @DisplayName("必須とオプショナルが混在する場合は必須のみチェック")
        void checksOnlyRequiredWhenMixed() {
            SkillParameter required = SkillParameter.required("target", "Target");
            SkillParameter optional = SkillParameter.optional("format", "Format", "JSON");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT,
                List.of(required, optional), Map.of()
            );

            // Required provided, optional not provided - should pass
            skill.validateParameters(Map.of("target", "value"));
        }
    }

    @Nested
    @DisplayName("不変性")
    class Immutability {

        @Test
        @DisplayName("parametersは不変リストである")
        void parametersIsImmutable() {
            SkillParameter param = SkillParameter.required("p", "desc");
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT,
                List.of(param), Map.of()
            );

            assertThatThrownBy(() -> skill.parameters().add(param))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("metadataは不変マップである")
        void metadataIsImmutable() {
            SkillDefinition skill = new SkillDefinition(
                VALID_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PROMPT,
                List.of(), Map.of("key", "value")
            );

            assertThatThrownBy(() -> skill.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
