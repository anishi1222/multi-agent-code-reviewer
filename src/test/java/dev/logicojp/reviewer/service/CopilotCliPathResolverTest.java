package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CopilotCliPathResolver")
class CopilotCliPathResolverTest {

    private final CopilotCliPathResolver resolver = new CopilotCliPathResolver();

    @Nested
    @DisplayName("resolveCliPath")
    class ResolveCliPath {

        @Test
        @DisplayName("COPILOT_CLI_PATHが設定されていない場合はシステムPATHから検索する")
        void fallsBackToSystemPathWhenEnvNotSet() {
            // CopilotCliPathResolver uses System.getenv internally.
            // Without COPILOT_CLI_PATH set, it falls back to PATH scanning.
            // If neither is found, a CopilotCliException is thrown.
            try {
                String result = resolver.resolveCliPath();
                // If found, it should not be blank
                assertThat(result).isNotBlank();
            } catch (CopilotCliException e) {
                // Expected in environments without Copilot CLI installed
                assertThat(e.getMessage()).containsAnyOf("not found", "PATH");
            }
        }

        @Test
        @DisplayName("信頼外ディレクトリのCOPILOT_CLI_PATHは拒否する")
        void rejectsUntrustedExplicitCliPath(@TempDir Path tempDir) throws IOException {
            Path fakeCli = tempDir.resolve("copilot");
            Files.writeString(fakeCli, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
            fakeCli.toFile().setExecutable(true);

            CopilotCliPathResolver resolver = new CopilotCliPathResolver(fakeCli.toString(), null);

            assertThatThrownBy(resolver::resolveCliPath)
                .isInstanceOf(CopilotCliException.class)
                .hasMessageContaining("trusted directories");
        }

        @Test
        @DisplayName("信頼外PATH上のcopilotは解決しない")
        void rejectsUntrustedPathCandidate(@TempDir Path tempDir) throws IOException {
            Path fakeCli = tempDir.resolve("copilot");
            Files.writeString(fakeCli, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
            fakeCli.toFile().setExecutable(true);

            CopilotCliPathResolver resolver = new CopilotCliPathResolver((String) null, tempDir.toString());

            assertThatThrownBy(resolver::resolveCliPath)
                .isInstanceOf(CopilotCliException.class)
                .hasMessageContaining("trusted PATH directories");
        }
    }

    @Nested
    @DisplayName("CLI_PATH_ENV定数")
    class Constants {

        @Test
        @DisplayName("CLI_PATH_ENV定数が期待される値を持つ")
        void cliPathEnvConstant() {
            assertThat(CopilotCliPathResolver.CLI_PATH_ENV).isEqualTo("COPILOT_CLI_PATH");
        }
    }
}
