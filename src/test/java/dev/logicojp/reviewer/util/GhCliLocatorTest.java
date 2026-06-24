package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GhCliLocator")
class GhCliLocatorTest {

    @Test
    @DisplayName("PATH未設定時はnullを返す")
    void returnsNullWhenPathIsMissing() {
        assertThat(new GhCliLocator(null, null).resolve()).isNull();
        assertThat(new GhCliLocator(null, "  ").resolve()).isNull();
    }

    @Test
    @DisplayName("信頼外PATH上のghは拒否する")
    void rejectsUntrustedGhInPath(@TempDir Path tempDir) throws IOException {
        Path fakeGh = tempDir.resolve("gh");
        Files.writeString(fakeGh, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        fakeGh.toFile().setExecutable(true);

        assertThat(new GhCliLocator(null, tempDir.toString()).resolve()).isNull();
    }

    @Test
    @DisplayName("明示パスが存在しない場合はnullを返す")
    void returnsNullForInvalidExplicitPath(@TempDir Path tempDir) {
        assertThat(new GhCliLocator(tempDir.resolve("missing-gh").toString(), null).resolve()).isNull();
    }
}
