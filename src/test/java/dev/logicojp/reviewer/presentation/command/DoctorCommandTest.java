package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.DiagnosticResult;
import dev.logicojp.reviewer.application.port.inbound.RunDiagnosticsPort;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.ExitCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DoctorCommand")
class DoctorCommandTest {

    @Test
    @DisplayName("全チェック成功時は終了コード0を返す")
    void returnsOkWhenAllChecksPass() {
        TestFixture fixture = TestFixture.withResults(List.of(
            DiagnosticResult.pass("CLI", "v1.2.3 (protocol 4)"),
            DiagnosticResult.pass("Auth", "octocat@github.com")
        ));
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout())
            .contains("All checks passed")
            .contains("\u2713")
            .contains("v1.2.3")
            .contains("(protocol 4)")
            .contains("octocat@github.com");
    }

    @Test
    @DisplayName("CLIが見つからない場合はUNAVAILABLEを返す")
    void returnsUnavailableWhenCliNotFound() {
        TestFixture fixture = TestFixture.withResults(List.of(
            DiagnosticResult.fail("CLI", "NOT FOUND", "CLI not found in PATH")
        ));
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("NOT FOUND");
        assertThat(fixture.stderr()).contains("issue(s) found");
        assertThat(fixture.runInvocations()).isEqualTo(1);
    }

    @Test
    @DisplayName("SDK初期化失敗時はUNAVAILABLEを返す")
    void returnsUnavailableWhenInitializationFails() {
        TestFixture fixture = TestFixture.withResults(List.of(
            DiagnosticResult.fail("Init", "FAILED", "client start failed")
        ));
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("Init: FAILED");
        assertThat(fixture.stdout()).contains("client start failed");
    }

    @Test
    @DisplayName("接続状態がCONNECTEDでない場合はFAILEDとして集計する")
    void reportsConnectionStateFailure() {
        TestFixture fixture = TestFixture.withResults(List.of(
            DiagnosticResult.fail("State", "DISCONNECTED")
        ));
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("State: DISCONNECTED");
    }

    @Test
    @DisplayName("認証されていないauthレスポンスはNOT AUTHENTICATEDとして集計する")
    void reportsAuthFailureWhenNotAuthenticated() {
        TestFixture fixture = TestFixture.withResults(List.of(
            DiagnosticResult.fail("Auth", "NOT AUTHENTICATED", "Run gh auth login")
        ));
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout())
            .contains("Auth: NOT AUTHENTICATED")
            .contains("Run gh auth login");
    }

    @Test
    @DisplayName("ヘルプ指定時は終了コード0を返す")
    void returnsOkWhenHelpRequested() {
        TestFixture fixture = TestFixture.withResults(List.of());
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[]{"--help"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout()).contains("Usage: review doctor");
        assertThat(fixture.runInvocations()).isZero();
    }

    @Test
    @DisplayName("不正オプション時はUSAGEを返す")
    void returnsUsageOnUnknownOption() {
        TestFixture fixture = TestFixture.withResults(List.of());
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[]{"--unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
        assertThat(fixture.stderr()).contains("Unknown option");
        assertThat(fixture.runInvocations()).isZero();
    }

    @Test
    @DisplayName("Java実行環境情報および診断結果見出しが表示される")
    void printsRuntimeAndConfiguration() {
        TestFixture fixture = TestFixture.withResults(List.of(
            DiagnosticResult.pass("CLI", "ok")
        ));
        DoctorCommand command = fixture.command();

        command.execute(new String[0]);

        assertThat(fixture.stdout())
            .contains("Runtime Environment")
            .contains("Java:")
            .contains("Diagnostic Results");
    }

    private static final class TestFixture {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));
        private final List<DiagnosticResult> results;
        private final AtomicInteger runCount = new AtomicInteger();

        private TestFixture(List<DiagnosticResult> results) {
            this.results = results;
        }

        static TestFixture withResults(List<DiagnosticResult> results) {
            return new TestFixture(results);
        }

        DoctorCommand command() {
            RunDiagnosticsPort diagnostics = () -> {
                runCount.incrementAndGet();
                return results;
            };
            return new DoctorCommand(diagnostics, output);
        }

        String stdout() {
            return out.toString();
        }

        String stderr() {
            return err.toString();
        }

        int runInvocations() {
            return runCount.get();
        }
    }
}
