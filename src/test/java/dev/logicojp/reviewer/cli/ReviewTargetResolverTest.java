package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.util.GitHubTokenResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewTargetResolver")
class ReviewTargetResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("repository target は指定トークンで解決できる")
    void resolvesRepositoryTargetWithProvidedToken() {
        var resolver = new ReviewTargetResolver(new GitHubTokenResolver(dev.logicojp.reviewer.config.ExecutionConfig.ofFlat(0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0)));

        ReviewTargetResolver.TargetAndToken result = resolver.resolve(
            new ReviewCommand.TargetSelection.Repository("owner/repo"),
            "  ghp_token  "
        );

        assertThat(result.target().displayName()).isEqualTo("owner/repo");
        assertThat(result.resolvedToken()).isEqualTo("ghp_token");
    }

    @Test
    @DisplayName("local target は絶対パスで解決しトークンは不要")
    void resolvesLocalDirectoryTarget() {
        var resolver = new ReviewTargetResolver(new GitHubTokenResolver(dev.logicojp.reviewer.config.ExecutionConfig.ofFlat(0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0)));

        ReviewTargetResolver.TargetAndToken result = resolver.resolve(
            new ReviewCommand.TargetSelection.LocalDirectory(tempDir),
            null
        );

        assertThat(result.target().isLocal()).isTrue();
        assertThat(result.target().localPath()).contains(tempDir.toAbsolutePath());
        assertThat(result.resolvedToken()).isNull();
    }

    @Test
    @DisplayName("存在しないローカルディレクトリはエラー")
    void throwsForMissingLocalDirectory() {
        var resolver = new ReviewTargetResolver(new GitHubTokenResolver(dev.logicojp.reviewer.config.ExecutionConfig.ofFlat(0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0)));
        Path missing = tempDir.resolve("missing");

        assertThatThrownBy(() -> resolver.resolve(
            new ReviewCommand.TargetSelection.LocalDirectory(missing),
            null
        ))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Local directory does not exist");
    }

    @Test
    @DisplayName("ディレクトリでないローカルパスはエラー")
    void throwsForNonDirectoryPath() throws Exception {
        var resolver = new ReviewTargetResolver(new GitHubTokenResolver(dev.logicojp.reviewer.config.ExecutionConfig.ofFlat(0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0)));
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "x");

        assertThatThrownBy(() -> resolver.resolve(
            new ReviewCommand.TargetSelection.LocalDirectory(file),
            null
        ))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Path is not a directory");
    }

    @Test
    @DisplayName("local target は指定トークンがあっても resolvedToken は null")
    void localTargetAlwaysResolvesNullToken() {
        var resolver = new ReviewTargetResolver(new GitHubTokenResolver(dev.logicojp.reviewer.config.ExecutionConfig.ofFlat(0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0)));

        ReviewTargetResolver.TargetAndToken result = resolver.resolve(
            new ReviewCommand.TargetSelection.LocalDirectory(tempDir),
            "ghp_token"
        );

        assertThat(result.resolvedToken()).isNull();
    }

    @Test
    @DisplayName("TargetAndToken の toString はトークン値を露出しない")
    void targetAndTokenToStringRedactsTokenValue() {
        var targetAndToken = new ReviewTargetResolver.TargetAndToken(
            dev.logicojp.reviewer.target.ReviewTarget.gitHub("owner/repo"),
            "ghp_secret"
        );

        assertThat(targetAndToken.toString())
            .contains("resolvedToken=***")
            .doesNotContain("ghp_secret");
    }
}
