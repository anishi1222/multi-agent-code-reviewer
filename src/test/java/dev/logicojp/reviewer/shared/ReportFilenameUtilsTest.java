package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportFilenameUtils")
class ReportFilenameUtilsTest {

    @Nested
    @DisplayName("sanitizeAgentName")
    class SanitizeAgentName {

        @Test
        @DisplayName("英数字とハイフン・ドット・アンダースコアはそのまま残す")
        void preservesValidCharacters() {
            assertThat(ReportFilenameUtils.sanitizeAgentName("agent-name_v1.0")).isEqualTo("agent-name_v1.0");
        }

        @Test
        @DisplayName("スペースをアンダースコアに置換する")
        void replacesSpaces() {
            assertThat(ReportFilenameUtils.sanitizeAgentName("my agent")).isEqualTo("my_agent");
        }

        @Test
        @DisplayName("特殊文字をアンダースコアに置換する")
        void replacesSpecialCharacters() {
            assertThat(ReportFilenameUtils.sanitizeAgentName("agent@v2!#$")).isEqualTo("agent_v2___");
        }

        @Test
        @DisplayName("日本語文字をアンダースコアに置換する")
        void replacesJapaneseCharacters() {
            String result = ReportFilenameUtils.sanitizeAgentName("セキュリティ");
            assertThat(result).doesNotContainPattern("[^a-zA-Z0-9._-]");
            assertThat(result).contains("_");
        }

        @Test
        @DisplayName("既にクリーンな名前はそのまま返す")
        void returnsCleanNameUnchanged() {
            assertThat(ReportFilenameUtils.sanitizeAgentName("security")).isEqualTo("security");
        }

        @Test
        @DisplayName("スラッシュをアンダースコアに置換する")
        void replacesSlashes() {
            assertThat(ReportFilenameUtils.sanitizeAgentName("path/to/agent")).isEqualTo("path_to_agent");
        }
    }
}
