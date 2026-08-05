package dev.logicojp.reviewer.domain.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillParameter")
class SkillParameterTest {

    @Nested
    @DisplayName("コンストラクタ - バリデーション")
    class ConstructorValidation {

        @Test
        @DisplayName("nameがnullの場合は例外を投げる")
        void nullNameThrows() {
            assertThatThrownBy(() -> new SkillParameter(
                null, "description", "string", true, null
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
        }

        @Test
        @DisplayName("nameが空白の場合は例外を投げる")
        void blankNameThrows() {
            assertThatThrownBy(() -> new SkillParameter(
                "  ", "description", "string", true, null
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
        }

        @Test
        @DisplayName("nameが空文字列の場合は例外を投げる")
        void emptyNameThrows() {
            assertThatThrownBy(() -> new SkillParameter(
                "", "description", "string", true, null
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
        }
    }

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("typeがnullの場合はstringになる")
        void nullTypeDefaultsToString() {
            SkillParameter param = new SkillParameter("name", "desc", null, true, null);
            assertThat(param.type()).isEqualTo("string");
        }

        @Test
        @DisplayName("typeが空白の場合はstringになる")
        void blankTypeDefaultsToString() {
            SkillParameter param = new SkillParameter("name", "desc", "  ", true, null);
            assertThat(param.type()).isEqualTo("string");
        }

        @Test
        @DisplayName("descriptionがnullの場合は空文字列になる")
        void nullDescriptionDefaultsToEmpty() {
            SkillParameter param = new SkillParameter("name", null, "string", true, null);
            assertThat(param.description()).isEmpty();
        }
    }

    @Nested
    @DisplayName("全引数コンストラクタ")
    class FullConstructor {

        @Test
        @DisplayName("すべてのフィールドが正しく設定される")
        void allFieldsSetCorrectly() {
            SkillParameter param = new SkillParameter(
                "targetFile", "The target file to process", "path", false, "/default/path"
            );

            assertThat(param.name()).isEqualTo("targetFile");
            assertThat(param.description()).isEqualTo("The target file to process");
            assertThat(param.type()).isEqualTo("path");
            assertThat(param.required()).isFalse();
            assertThat(param.defaultValue()).isEqualTo("/default/path");
        }

        @Test
        @DisplayName("必須パラメータを作成できる")
        void canCreateRequiredParameter() {
            SkillParameter param = new SkillParameter(
                "input", "Input value", "string", true, null
            );

            assertThat(param.required()).isTrue();
            assertThat(param.defaultValue()).isNull();
        }
    }

    @Nested
    @DisplayName("ファクトリメソッド - required")
    class RequiredFactory {

        @Test
        @DisplayName("必須パラメータを作成する")
        void createsRequiredParameter() {
            SkillParameter param = SkillParameter.required("repository", "The repository to review");

            assertThat(param.name()).isEqualTo("repository");
            assertThat(param.description()).isEqualTo("The repository to review");
            assertThat(param.type()).isEqualTo("string");
            assertThat(param.required()).isTrue();
            assertThat(param.defaultValue()).isNull();
        }
    }

    @Nested
    @DisplayName("ファクトリメソッド - optional")
    class OptionalFactory {

        @Test
        @DisplayName("オプショナルパラメータを作成する")
        void createsOptionalParameter() {
            SkillParameter param = SkillParameter.optional(
                "format", "Output format", "json"
            );

            assertThat(param.name()).isEqualTo("format");
            assertThat(param.description()).isEqualTo("Output format");
            assertThat(param.type()).isEqualTo("string");
            assertThat(param.required()).isFalse();
            assertThat(param.defaultValue()).isEqualTo("json");
        }

        @Test
        @DisplayName("デフォルト値がnullでもオプショナルパラメータを作成できる")
        void canCreateOptionalWithNullDefault() {
            SkillParameter param = SkillParameter.optional(
                "extra", "Extra options", null
            );

            assertThat(param.required()).isFalse();
            assertThat(param.defaultValue()).isNull();
        }
    }

    @Nested
    @DisplayName("レコードの等価性")
    class RecordEquality {

        @Test
        @DisplayName("同じ値を持つパラメータは等価である")
        void sameValuesAreEqual() {
            SkillParameter param1 = new SkillParameter("name", "desc", "string", true, null);
            SkillParameter param2 = new SkillParameter("name", "desc", "string", true, null);

            assertThat(param1).isEqualTo(param2);
            assertThat(param1.hashCode()).isEqualTo(param2.hashCode());
        }

        @Test
        @DisplayName("異なる名前を持つパラメータは等価でない")
        void differentNamesAreNotEqual() {
            SkillParameter param1 = new SkillParameter("name1", "desc", "string", true, null);
            SkillParameter param2 = new SkillParameter("name2", "desc", "string", true, null);

            assertThat(param1).isNotEqualTo(param2);
        }

        @Test
        @DisplayName("異なるrequired設定を持つパラメータは等価でない")
        void differentRequiredAreNotEqual() {
            SkillParameter param1 = new SkillParameter("name", "desc", "string", true, null);
            SkillParameter param2 = new SkillParameter("name", "desc", "string", false, null);

            assertThat(param1).isNotEqualTo(param2);
        }

        @Test
        @DisplayName("異なるデフォルト値を持つパラメータは等価でない")
        void differentDefaultValuesAreNotEqual() {
            SkillParameter param1 = SkillParameter.optional("name", "desc", "value1");
            SkillParameter param2 = SkillParameter.optional("name", "desc", "value2");

            assertThat(param1).isNotEqualTo(param2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toStringは主要なフィールドを含む")
        void toStringContainsFields() {
            SkillParameter param = new SkillParameter(
                "myParam", "My description", "number", true, "42"
            );

            String result = param.toString();

            assertThat(result).contains("myParam");
            assertThat(result).contains("number");
        }
    }
}
