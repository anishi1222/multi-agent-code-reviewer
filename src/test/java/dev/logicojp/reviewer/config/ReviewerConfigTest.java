package dev.logicojp.reviewer.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


@DisplayName("ReviewerConfig")
class ReviewerConfigTest {

    @Nested
    @DisplayName("AgentPaths")
    class AgentPathsTests {

        @Test
        @DisplayName("nullの場合はデフォルトディレクトリが設定される")
        void nullDefaultsToDefaultDirectories() {
            var paths = new ReviewerConfig.AgentPaths(null);
            Assertions.assertThat(paths.directories()).isEqualTo(ReviewerConfig.AgentPaths.DEFAULT_DIRECTORIES);
        }

        @Test
        @DisplayName("空リストの場合はデフォルトディレクトリが設定される")
        void emptyDefaultsToDefaultDirectories() {
            var paths = new ReviewerConfig.AgentPaths(java.util.List.of());
            Assertions.assertThat(paths.directories()).isEqualTo(ReviewerConfig.AgentPaths.DEFAULT_DIRECTORIES);
        }

        @Test
        @DisplayName("指定されたディレクトリがそのまま保持される")
        void preservesProvidedDirectories() {
            var dirs = java.util.List.of("./custom-agents");
            var paths = new ReviewerConfig.AgentPaths(dirs);
            Assertions.assertThat(paths.directories()).containsExactly("./custom-agents");
        }

        @Test
        @DisplayName("ディレクトリリストは不変である")
        void directoriesAreImmutable() {
            var paths = new ReviewerConfig.AgentPaths(null);
            Assertions.assertThat(paths.directories()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("LocalFiles")
    class LocalFilesTests {

        @Test
        @DisplayName("0以下のサイズはデフォルトに設定される")
        void nonPositiveSizesDefaulted() {
            var files = new ReviewerConfig.LocalFiles(0, 0, null, null, null, null);

            Assertions.assertThat(files.maxFileSize()).isEqualTo(ReviewerConfig.LocalFiles.DEFAULT_MAX_FILE_SIZE);
            Assertions.assertThat(files.maxTotalSize()).isEqualTo(ReviewerConfig.LocalFiles.DEFAULT_MAX_TOTAL_SIZE);
        }

        @Test
        @DisplayName("nullリストはデフォルトリストに設定される")
        void nullListsDefaulted() {
            var files = new ReviewerConfig.LocalFiles(0, 0, null, null, null, null);

            Assertions.assertThat(files.ignoredDirectories()).isNotEmpty();
            Assertions.assertThat(files.sourceExtensions()).isNotEmpty();
            Assertions.assertThat(files.sensitiveFilePatterns()).isNotEmpty();
            Assertions.assertThat(files.sensitiveExtensions()).isNotEmpty();
        }

        @Test
        @DisplayName("引数なしコンストラクタはデフォルト値を使用する")
        void noArgConstructor() {
            var files = new ReviewerConfig.LocalFiles();

            Assertions.assertThat(files.maxFileSize()).isEqualTo(ReviewerConfig.LocalFiles.DEFAULT_MAX_FILE_SIZE);
            Assertions.assertThat(files.maxTotalSize()).isEqualTo(ReviewerConfig.LocalFiles.DEFAULT_MAX_TOTAL_SIZE);
        }
    }

    @Nested
    @DisplayName("Skills")
    class SkillsTests {

        @Test
        @DisplayName("nullフィールドはデフォルト値に設定される")
        void nullFieldsDefaulted() {
            var skills = new ReviewerConfig.Skills(null, null, 0, 0, 0, 0, 0);

            Assertions.assertThat(skills.filename()).isEqualTo("SKILL.md");
            Assertions.assertThat(skills.directory()).isEqualTo(".github/skills");
            Assertions.assertThat(skills.maxParameterValueLength())
                .isEqualTo(ReviewerConfig.Skills.DEFAULT_MAX_PARAMETER_VALUE_LENGTH);
        }

        @Test
        @DisplayName("defaultsファクトリはデフォルト値のインスタンスを返す")
        void defaultsFactory() {
            var skills = ReviewerConfig.Skills.defaults();

            Assertions.assertThat(skills.filename()).isEqualTo("SKILL.md");
            Assertions.assertThat(skills.directory()).isEqualTo(".github/skills");
        }
    }

    @Nested
    @DisplayName("ReviewerConfig全体")
    class TopLevel {

        @Test
        @DisplayName("nullネストレコードはデフォルトインスタンスに設定される")
        void nullNestedRecordsDefaulted() {
            var config = new ReviewerConfig(null, null, null);

            Assertions.assertThat(config.agents()).isNotNull();
            Assertions.assertThat(config.localFiles()).isNotNull();
            Assertions.assertThat(config.skills()).isNotNull();
        }
    }
}
