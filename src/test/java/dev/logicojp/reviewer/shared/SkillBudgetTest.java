package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillBudget")
class SkillBudgetTest {

    @Test
    @DisplayName("既定コンストラクタはConfigDefaultsの既定値を採用する")
    void defaultsToConfigDefault() {
        assertThat(new SkillBudget().renderedSkillSectionMaxChars())
            .isEqualTo(ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH);
    }

    @Test
    @DisplayName("正の値はそのまま保持される")
    void keepsPositiveValue() {
        assertThat(new SkillBudget(4_242).renderedSkillSectionMaxChars()).isEqualTo(4_242);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("非正の値は既定値に正規化される")
    void normalizesNonPositiveToDefault(int configured) {
        assertThat(new SkillBudget(configured).renderedSkillSectionMaxChars())
            .isEqualTo(SkillBudget.DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS);
    }

    @Test
    @DisplayName("既定値はConfigDefaultsの定数を単一の出所とする")
    void defaultConstantTracksConfigDefaults() {
        assertThat(SkillBudget.DEFAULT_RENDERED_SKILL_SECTION_MAX_CHARS)
            .isEqualTo(ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH);
    }
}
