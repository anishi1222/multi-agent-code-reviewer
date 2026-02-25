package dev.logicojp.reviewer.target;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;


@DisplayName("ReviewTarget")
class ReviewTargetTest {

    @Nested
    @DisplayName("gitHub - ファクトリメソッド")
    class GitHubFactory {

        @Test
        @DisplayName("有効なowner/repo形式でGitHubTargetを生成する")
        void createsGitHubTarget() {
            ReviewTarget target = ReviewTarget.gitHub("owner/repo");

            Assertions.assertThat(target).isInstanceOf(ReviewTarget.GitHubTarget.class);
            Assertions.assertThat(target.displayName()).isEqualTo("owner/repo");
            Assertions.assertThat(target.isLocal()).isFalse();
        }

        @Test
        @DisplayName("nullの場合は例外をスローする")
        void throwsForNull() {
            Assertions.assertThatThrownBy(() -> ReviewTarget.gitHub(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空文字列の場合は例外をスローする")
        void throwsForBlank() {
            Assertions.assertThatThrownBy(() -> ReviewTarget.gitHub("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("不正な形式の場合は例外をスローする")
        void throwsForInvalidFormat() {
            Assertions.assertThatThrownBy(() -> ReviewTarget.gitHub("no-slash"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("パストラバーサルを含む場合は例外をスローする")
        void throwsForTraversal() {
            Assertions.assertThatThrownBy(() -> ReviewTarget.gitHub("../attack"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("ドットセグメントは拒否される")
        void rejectsDotSegments() {
            Assertions.assertThatThrownBy(() -> ReviewTarget.gitHub("./repo"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("local - ファクトリメソッド")
    class LocalFactory {

        @Test
        @DisplayName("有効なPathでLocalTargetを生成する")
        void createsLocalTarget(@TempDir Path tempDir) {
            ReviewTarget target = ReviewTarget.local(tempDir);

            Assertions.assertThat(target).isInstanceOf(ReviewTarget.LocalTarget.class);
            Assertions.assertThat(target.isLocal()).isTrue();
            Assertions.assertThat(target.localPath()).isPresent();
        }

        @Test
        @DisplayName("nullの場合は例外をスローする")
        void throwsForNull() {
            Assertions.assertThatThrownBy(() -> ReviewTarget.local(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("displayName")
    class DisplayNameTests {

        @Test
        @DisplayName("GitHubTargetはリポジトリ名を返す")
        void gitHubReturnsRepository() {
            Assertions.assertThat(ReviewTarget.gitHub("owner/repo").displayName())
                .isEqualTo("owner/repo");
        }

        @Test
        @DisplayName("LocalTargetはディレクトリ名を返す")
        void localReturnsDirectoryName(@TempDir Path tempDir) {
            Assertions.assertThat(ReviewTarget.local(tempDir).displayName())
                .isEqualTo(tempDir.getFileName().toString());
        }
    }

    @Nested
    @DisplayName("localPath")
    class LocalPathTests {

        @Test
        @DisplayName("GitHubTargetはemptyを返す")
        void gitHubReturnsEmpty() {
            Assertions.assertThat(ReviewTarget.gitHub("owner/repo").localPath()).isEmpty();
        }

        @Test
        @DisplayName("LocalTargetはPathを返す")
        void localReturnsPath(@TempDir Path tempDir) {
            Assertions.assertThat(ReviewTarget.local(tempDir).localPath())
                .isPresent()
                .hasValue(tempDir);
        }
    }

    @Nested
    @DisplayName("repositorySubPath")
    class RepositorySubPath {

        @Test
        @DisplayName("GitHubTargetはリポジトリのサブパスを返す")
        void gitHubReturnsSubPath() {
            Path subPath = ReviewTarget.gitHub("owner/repo").repositorySubPath();
            Assertions.assertThat(subPath).isEqualTo(Path.of("owner/repo"));
        }

        @Test
        @DisplayName("LocalTargetはディレクトリ名のPathを返す")
        void localReturnsDirectoryNamePath(@TempDir Path tempDir) {
            Path subPath = ReviewTarget.local(tempDir).repositorySubPath();
            Assertions.assertThat(subPath.toString()).isEqualTo(tempDir.getFileName().toString());
        }
    }

    @Nested
    @DisplayName("パターンマッチング")
    class PatternMatching {

        @Test
        @DisplayName("switch式でGitHubTargetをマッチングできる")
        void matchesGitHubTarget() {
            ReviewTarget target = ReviewTarget.gitHub("owner/repo");
            String result = switch (target) {
                case ReviewTarget.GitHubTarget(String repository) -> "GitHub: " + repository;
                case ReviewTarget.LocalTarget(_) -> "Local";
            };
            Assertions.assertThat(result).isEqualTo("GitHub: owner/repo");
        }

        @Test
        @DisplayName("switch式でLocalTargetをマッチングできる")
        void matchesLocalTarget(@TempDir Path tempDir) {
            ReviewTarget target = ReviewTarget.local(tempDir);
            String result = switch (target) {
                case ReviewTarget.GitHubTarget(_) -> "GitHub";
                case ReviewTarget.LocalTarget(Path directory) -> "Local: " + directory;
            };
            Assertions.assertThat(result).startsWith("Local: ");
        }
    }
}
