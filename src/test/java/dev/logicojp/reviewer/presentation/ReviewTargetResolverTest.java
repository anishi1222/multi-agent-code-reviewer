package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewTargetResolver")
class ReviewTargetResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("repository target は指定トークンで解決できる")
    void resolvesRepositoryTargetWithProvidedToken() {
        var resolver = new ReviewTargetResolver(trimmingTokenResolver());

        ReviewTargetResolver.TargetAndToken result = resolver.resolve(
            new ReviewTargetSelection.Repository("owner/repo"),
            "  ghp_token  "
        );

        assertThat(result.target().displayName()).isEqualTo("owner/repo");
        assertThat(result.resolvedToken()).isEqualTo("ghp_token");
    }

    @Test
    @DisplayName("local target は絶対パスで解決しトークンは不要")
    void resolvesLocalDirectoryTarget() {
        var resolver = new ReviewTargetResolver(trimmingTokenResolver());

        ReviewTargetResolver.TargetAndToken result = resolver.resolve(
            new ReviewTargetSelection.LocalDirectory(tempDir),
            null
        );

        assertThat(result.target().isLocal()).isTrue();
        assertThat(result.target().localPath()).contains(tempDir.toAbsolutePath());
        assertThat(result.resolvedToken()).isNull();
    }

    @Test
    @DisplayName("存在しないローカルディレクトリはエラー")
    void throwsForMissingLocalDirectory() {
        var resolver = new ReviewTargetResolver(trimmingTokenResolver());
        Path missing = tempDir.resolve("missing");

        assertThatThrownBy(() -> resolver.resolve(
            new ReviewTargetSelection.LocalDirectory(missing),
            null
        ))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Local directory does not exist");
    }

    @Test
    @DisplayName("ディレクトリでないローカルパスはエラー")
    void throwsForNonDirectoryPath() throws Exception {
        var resolver = new ReviewTargetResolver(trimmingTokenResolver());
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "x");

        assertThatThrownBy(() -> resolver.resolve(
            new ReviewTargetSelection.LocalDirectory(file),
            null
        ))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Path is not a directory");
    }

    @Test
    @DisplayName("local target は指定トークンがあっても resolvedToken は null")
    void localTargetAlwaysResolvesNullToken() {
        var resolver = new ReviewTargetResolver(trimmingTokenResolver());

        ReviewTargetResolver.TargetAndToken result = resolver.resolve(
            new ReviewTargetSelection.LocalDirectory(tempDir),
            "ghp_token"
        );

        assertThat(result.resolvedToken()).isNull();
    }

    private static ResolveTokenPort trimmingTokenResolver() {
        return token -> Optional.ofNullable(token)
            .map(String::trim)
            .filter(value -> !value.isBlank());
    }

    @Test
    @DisplayName("TargetAndToken の toString はトークン値を露出しない")
    void targetAndTokenToStringRedactsTokenValue() {
        var targetAndToken = new ReviewTargetResolver.TargetAndToken(
            dev.logicojp.reviewer.domain.review.ReviewTarget.gitHub("owner/repo"),
            "ghp_secret"
        );

        assertThat(targetAndToken.toString())
            .contains("resolvedToken=***")
            .doesNotContain("ghp_secret");
    }
}
