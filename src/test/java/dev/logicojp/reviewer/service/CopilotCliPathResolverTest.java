package dev.logicojp.reviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
