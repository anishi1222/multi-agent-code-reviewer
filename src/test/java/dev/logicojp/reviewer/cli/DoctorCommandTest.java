package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.CopilotCliHealthChecker;
import dev.logicojp.reviewer.service.CopilotCliPathResolver;
import dev.logicojp.reviewer.service.CopilotTimeoutResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DoctorCommand")
class DoctorCommandTest {

    @Test
    @DisplayName("全チェック成功時は終了コード0を返す")
    void returnsOkWhenAllCheckPass() {
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", true, true);
        DoctorCommand command = fixture.build();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout()).contains("All checks passed");
        assertThat(fixture.stdout()).contains("\u2713"); // checkmark
    }

    @Test
    @DisplayName("CLIが見つからない場合はUNAVAILABLEを返す")
    void returnsUnavailableWhenCliNotFound() {
        TestFixture fixture = new TestFixture(null, false, false);
        DoctorCommand command = fixture.build();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("NOT FOUND");
        assertThat(fixture.stderr()).contains("issue(s) found");
    }

    @Test
    @DisplayName("CLIは見つかるがヘルスチェック失敗時はUNAVAILABLEを返す")
    void returnsUnavailableWhenHealthCheckFails() {
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", false, false);
        DoctorCommand command = fixture.build();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("FAILED");
    }

    @Test
    @DisplayName("auth未サポートCLIバージョンでもSKIPPEDとして成功扱い")
    void treatsUnsupportedAuthAsSkipped() {
        // version passes, auth returns false (unsupported)
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", true, false);
        DoctorCommand command = fixture.buildWithAuthUnsupported();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout()).contains("SKIPPED");
    }

    @Test
    @DisplayName("ヘルプ指定時は終了コード0を返す")
    void returnsOkWhenHelpRequested() {
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", true, true);
        DoctorCommand command = fixture.build();

        int exit = command.execute(new String[]{"--help"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout()).contains("Usage: review doctor");
    }

    @Test
    @DisplayName("不正オプション時はUSAGEを返す")
    void returnsUsageOnUnknownOption() {
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", true, true);
        DoctorCommand command = fixture.build();

        int exit = command.execute(new String[]{"--unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
        assertThat(fixture.stderr()).contains("Unknown option");
    }

    @Test
    @DisplayName("Java実行環境情報が表示される")
    void printsJavaRuntimeInfo() {
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", true, true);
        DoctorCommand command = fixture.build();

        command.execute(new String[0]);

        assertThat(fixture.stdout()).contains("Runtime Environment");
        assertThat(fixture.stdout()).contains("Java:");
    }

    @Test
    @DisplayName("設定情報が表示される")
    void printsConfigurationInfo() {
        TestFixture fixture = new TestFixture("/usr/local/bin/github-copilot", true, true);
        DoctorCommand command = fixture.build();

        command.execute(new String[0]);

        assertThat(fixture.stdout()).contains("Configuration");
        assertThat(fixture.stdout()).contains("Start timeout:");
        assertThat(fixture.stdout()).contains("Healthcheck timeout:");
        assertThat(fixture.stdout()).contains("Auth check timeout:");
    }

    /// Test fixture for constructing DoctorCommand with controlled behavior.
    private static final class TestFixture {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));
        private final String cliPath;
        private final boolean versionOk;
        private final boolean authOk;

        TestFixture(String cliPath, boolean versionOk, boolean authOk) {
            this.cliPath = cliPath;
            this.versionOk = versionOk;
            this.authOk = authOk;
        }

        DoctorCommand build() {
            return new DoctorCommand(
                stubPathResolver(),
                stubHealthChecker(versionOk, authOk, true),
                new CopilotTimeoutResolver(),
                output
            );
        }

        DoctorCommand buildWithAuthUnsupported() {
            return new DoctorCommand(
                stubPathResolver(),
                stubHealthChecker(versionOk, false, false),
                new CopilotTimeoutResolver(),
                output
            );
        }

        String stdout() {
            return out.toString();
        }

        String stderr() {
            return err.toString();
        }

        private CopilotCliPathResolver stubPathResolver() {
            var config = new dev.logicojp.reviewer.config.CopilotConfig(
                cliPath, null, 60, 10, 15);
            return new CopilotCliPathResolver(config, System.getenv("PATH")) {
                @Override
                public String resolveCliPath() {
                    if (cliPath == null) {
                        throw new CopilotCliException("CLI not found in PATH");
                    }
                    return cliPath;
                }
            };
        }

        private CopilotCliHealthChecker stubHealthChecker(boolean versionOk,
                                                           boolean authResult,
                                                           boolean authSupported) {
            return new CopilotCliHealthChecker(new CopilotTimeoutResolver()) {
                @Override
                public void checkCliVersion(String cliPath) {
                    if (!versionOk) {
                        throw new CopilotCliException("Version check failed");
                    }
                }

                @Override
                public boolean checkCliAuth(String cliPath) {
                    if (!authSupported) {
                        return false; // CLI doesn't support auth status
                    }
                    if (!authResult) {
                        throw new CopilotCliException("Auth check failed");
                    }
                    return true;
                }
            };
        }
    }
}
