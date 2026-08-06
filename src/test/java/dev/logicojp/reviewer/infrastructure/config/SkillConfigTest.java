package dev.logicojp.reviewer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillConfig")
class SkillConfigTest {

    @Nested
    @DisplayName("コンストラクタ - デフォルト値")
    class DefaultValues {

        @Test
        @DisplayName("filenameがnullの場合はSKILL.mdに設定される")
        void filenameNullDefaultsToSkillMd() {
            SkillConfig config = SkillConfig.defaults();
            assertThat(config.filename()).isEqualTo("SKILL.md");
        }

        @Test
        @DisplayName("filenameが空文字の場合はSKILL.mdに設定される")
        void filenameBlankDefaultsToSkillMd() {
            SkillConfig config = new SkillConfig("  ", null, 0, 0, 0, 0.0, 0, 0);
            assertThat(config.filename()).isEqualTo("SKILL.md");
        }

        @Test
        @DisplayName("directoryがnullの場合は.github/skillsに設定される")
        void directoryNullDefaultsToGithubSkills() {
            SkillConfig config = SkillConfig.defaults();
            assertThat(config.directory()).isEqualTo(".github/skills");
        }

        @Test
        @DisplayName("directoryが空文字の場合は.github/skillsに設定される")
        void directoryBlankDefaultsToGithubSkills() {
            SkillConfig config = new SkillConfig(null, "  ", 0, 0, 0, 0.0, 0, 0);
            assertThat(config.directory()).isEqualTo(".github/skills");
        }
    }

    @Nested
    @DisplayName("コンストラクタ - カスタム値")
    class CustomValues {

        @Test
        @DisplayName("カスタムファイル名が設定される")
        void customFilename() {
            SkillConfig config = new SkillConfig("CUSTOM.md", null, 0, 0, 0, 0.0, 0, 0);
            assertThat(config.filename()).isEqualTo("CUSTOM.md");
        }

        @Test
        @DisplayName("カスタムディレクトリが設定される")
        void customDirectory() {
            SkillConfig config = new SkillConfig(null, "custom/skills", 0, 0, 0, 0.0, 0, 0);
            assertThat(config.directory()).isEqualTo("custom/skills");
        }

        @Test
        @DisplayName("すべてのカスタム値が設定される")
        void allCustomValues() {
            SkillConfig config = new SkillConfig("CUSTOM.md", "custom/skills", 0, 0, 0, 0.0, 0, 0);
            assertThat(config.filename()).isEqualTo("CUSTOM.md");
            assertThat(config.directory()).isEqualTo("custom/skills");
        }
    }
}
