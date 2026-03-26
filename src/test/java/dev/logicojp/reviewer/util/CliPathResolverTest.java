package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CliPathResolver")
class CliPathResolverTest {

    @Test
    @DisplayName("空の明示パスはemptyを返す")
    void blankExplicitPathReturnsEmpty() {
        Optional<java.nio.file.Path> resolved = CliPathResolver.resolveExplicitExecutable("  ", "gh");
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("存在しないPATH候補の場合はemptyを返す")
    void notFoundInPathReturnsEmpty() {
        Optional<java.nio.file.Path> resolved = CliPathResolver.findExecutableInPathValue(
            "/path/that/does/not/exist",
            "___no_such_bin___"
        );
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("信頼外PATHエントリの実行可能ファイルは解決しない")
    void untrustedPathExecutableIsRejected(@TempDir Path tempDir) throws IOException {
        Path fakeGh = tempDir.resolve("gh");
        Files.writeString(fakeGh, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        fakeGh.toFile().setExecutable(true);

        Optional<Path> resolved = CliPathResolver.findTrustedExecutableInPathValue(
            tempDir.toString(),
            "gh"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("実行直前の再検証で実パス不一致はemptyを返す")
    void revalidateExecutionPathRejectsRealPathMismatch() throws IOException {
        String symlinkPath = "/bin/true";
        Path realPath = Path.of(symlinkPath).toRealPath();
        if (!realPath.toString().equals(Path.of(symlinkPath).toAbsolutePath().normalize().toString())) {
            Optional<Path> resolved = CliPathResolver.revalidateExecutionPath(symlinkPath, "true");
            assertThat(resolved).isEmpty();
        }
    }

    @Test
    @DisplayName("信頼済みディレクトリ配下はtrueを返す")
    void trustedDirectoryReturnsTrue() {
        assertThat(CliPathResolver.isInTrustedDirectory(Path.of("/usr/bin/gh"))).isTrue();
        assertThat(CliPathResolver.isInTrustedDirectory(Path.of("/usr/local/bin/gh"))).isTrue();
    }

    @Test
    @DisplayName("信頼外ディレクトリ配下はfalseを返す")
    void untrustedDirectoryReturnsFalse() {
        assertThat(CliPathResolver.isInTrustedDirectory(Path.of("/tmp/gh"))).isFalse();
    }
}
