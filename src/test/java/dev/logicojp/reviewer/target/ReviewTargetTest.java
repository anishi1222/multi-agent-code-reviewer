package dev.logicojp.reviewer.target;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewTarget")
class ReviewTargetTest {

    @Nested
    @DisplayName("ファクトリメソッド")
    class FactoryMethods {

        @Test
        @DisplayName("gitHubはGitHubTargetを返す")
        void gitHubCreatesGitHubTarget() {
            ReviewTarget target = ReviewTarget.gitHub("owner/repo");
            assertThat(target).isInstanceOf(ReviewTarget.GitHubTarget.class);
        }

        @Test
        @DisplayName("localはLocalTargetを返す")
        void localCreatesLocalTarget() {
            ReviewTarget target = ReviewTarget.local(Path.of("/tmp/test"));
            assertThat(target).isInstanceOf(ReviewTarget.LocalTarget.class);
        }

        @Test
        @DisplayName("nullリポジトリはIllegalArgumentExceptionをスローする")
        void gitHubRejectsNull() {
            assertThatThrownBy(() -> ReviewTarget.gitHub(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空白リポジトリはIllegalArgumentExceptionをスローする")
        void gitHubRejectsBlank() {
            assertThatThrownBy(() -> ReviewTarget.gitHub("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("owner/repo形式でないリポジトリはIllegalArgumentExceptionをスローする")
        void gitHubRejectsInvalidFormat() {
            assertThatThrownBy(() -> ReviewTarget.gitHub("invalid-repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid repository format");
        }

        @Test
        @DisplayName("nullディレクトリはIllegalArgumentExceptionをスローする")
        void localRejectsNull() {
            assertThatThrownBy(() -> ReviewTarget.local(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("displayName")
    class DisplayNameTests {

        @Test
        @DisplayName("GitHubTargetはリポジトリ名を返す")
        void gitHubDisplayName() {
            ReviewTarget target = ReviewTarget.gitHub("owner/repo");
            assertThat(target.displayName()).isEqualTo("owner/repo");
        }

        @Test
        @DisplayName("LocalTargetはディレクトリ名を返す")
        void localDisplayName() {
            ReviewTarget target = ReviewTarget.local(Path.of("/tmp/my-project"));
            assertThat(target.displayName()).isEqualTo("my-project");
        }
    }

    @Nested
    @DisplayName("isLocal")
    class IsLocalTests {

        @Test
        @DisplayName("GitHubTargetはfalseを返す")
        void gitHubIsNotLocal() {
            assertThat(ReviewTarget.gitHub("owner/repo").isLocal()).isFalse();
        }

        @Test
        @DisplayName("LocalTargetはtrueを返す")
        void localIsLocal() {
            assertThat(ReviewTarget.local(Path.of("/tmp")).isLocal()).isTrue();
        }
    }

    @Nested
    @DisplayName("localPath")
    class LocalPathTests {

        @Test
        @DisplayName("LocalTargetはパスを含むOptionalを返す")
        void localTargetReturnsPath() {
            Path dir = Path.of("/tmp/project");
            assertThat(ReviewTarget.local(dir).localPath()).contains(dir);
        }

        @Test
        @DisplayName("GitHubTargetは空のOptionalを返す")
        void gitHubTargetReturnsEmpty() {
            assertThat(ReviewTarget.gitHub("o/r").localPath()).isEmpty();
        }
    }

    @Nested
    @DisplayName("repositorySubPath")
    class RepositorySubPathTests {

        @Test
        @DisplayName("GitHubTargetはowner/repoパスを返す")
        void gitHubSubPath() {
            ReviewTarget target = ReviewTarget.gitHub("anishi1222/project");
            assertThat(target.repositorySubPath()).isEqualTo(Path.of("anishi1222/project"));
        }

        @Test
        @DisplayName("LocalTargetはディレクトリ名パスを返す")
        void localSubPath() {
            ReviewTarget target = ReviewTarget.local(Path.of("/home/user/my-project"));
            assertThat(target.repositorySubPath()).isEqualTo(Path.of("my-project"));
        }

        @Test
        @DisplayName("ルートディレクトリのLocalTargetは安全な相対パスを返す")
        void localRootSubPathUsesSafeRelativeName() {
            ReviewTarget target = ReviewTarget.local(Path.of("/"));
            assertThat(target.repositorySubPath()).isEqualTo(Path.of("local-root"));
        }

        @Test
        @DisplayName("パストラバーサル文字を含むリポジトリ名は拒否される")
        void rejectsPathTraversal() {
            assertThatThrownBy(() -> ReviewTarget.gitHub("../../tmp/malicious"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid repository format");
        }

        @Test
        @DisplayName("絶対パスで始まるリポジトリ名は拒否される")
        void rejectsAbsolutePath() {
            assertThatThrownBy(() -> ReviewTarget.gitHub("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid repository format");
        }

        @Test
        @DisplayName("owner/.. のようなドットセグメントを含むリポジトリ名は拒否される")
        void rejectsDotSegmentTraversal() {
            assertThatThrownBy(() -> ReviewTarget.gitHub("owner/.."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal segment");
        }
    }

    @Nested
    @DisplayName("パターンマッチング")
    class PatternMatching {

        @Test
        @DisplayName("switchでGitHubTargetをパターンマッチできる")
        void gitHubPatternMatch() {
            ReviewTarget target = ReviewTarget.gitHub("owner/repo");
            String result = switch (target) {
                case ReviewTarget.GitHubTarget(String repository) -> "github:" + repository;
                case ReviewTarget.LocalTarget(Path _) -> "local";
            };
            assertThat(result).isEqualTo("github:owner/repo");
        }

        @Test
        @DisplayName("switchでLocalTargetをパターンマッチできる")
        void localPatternMatch() {
            ReviewTarget target = ReviewTarget.local(Path.of("/tmp"));
            String result = switch (target) {
                case ReviewTarget.GitHubTarget(_) -> "github";
                case ReviewTarget.LocalTarget(Path directory) -> "local:" + directory;
            };
            assertThat(result).isEqualTo("local:/tmp");
        }
    }
}
