package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CopilotCliHealthChecker")
class CopilotCliHealthCheckerTest {

    private final CopilotCliHealthChecker checker = new CopilotCliHealthChecker(new CopilotTimeoutResolver());

    @Test
    @DisplayName("cliPath が空の場合は何もしない")
    void doesNothingWhenCliPathIsBlank() {
        assertThatCode(() -> checker.verifyCliHealthy("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("正常なCLIパスではヘルスチェックが成功する")
    void succeedsWithValidCliPath(@TempDir Path tempDir) throws IOException {
        Path fakeCli = tempDir.resolve("github-copilot");
        Files.writeString(fakeCli, "#!/usr/bin/env sh\nexit 0\n", StandardCharsets.UTF_8);
        fakeCli.toFile().setExecutable(true);
        assertThatCode(() -> checker.verifyCliHealthy(fakeCli.toRealPath().toString()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("不正なCLIパスではCopilotCliExceptionを送出する")
    void throwsWhenCliPathIsInvalid() {
        assertThatThrownBy(() -> checker.verifyCliHealthy("/path/does/not/exist"))
            .isInstanceOf(CopilotCliException.class)
            .hasMessageContaining("execution-time validation");
    }
}
