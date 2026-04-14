package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliCommand;
import dev.logicojp.reviewer.cli.CliOutput;
import dev.logicojp.reviewer.cli.ExitCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewApp")
class ReviewAppTest {

    private static final CliOutput NULL_OUTPUT = new CliOutput(
        new PrintStream(OutputStream.nullOutputStream()),
        new PrintStream(OutputStream.nullOutputStream())
    );

    @Test
    @DisplayName("runサブコマンドをReviewCommandに委譲する")
    void delegatesRunCommand() {
        AtomicInteger runCalled = new AtomicInteger();

        CliCommand runCmd = new CliCommand() {
            @Override
            public String name() { return "run"; }

            @Override
            public int execute(String[] args) {
                runCalled.incrementAndGet();
                return 42;
            }
        };
        CliCommand listCmd = stubCommand("list", 0);
        CliCommand skillCmd = stubCommand("skill", 0);

        ReviewApp app = new ReviewApp(List.of(runCmd, listCmd, skillCmd), NULL_OUTPUT);
        int exit = app.execute(new String[]{"run"});

        assertThat(exit).isEqualTo(42);
        assertThat(runCalled.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("未知コマンドではUSAGEを返す")
    void returnsUsageForUnknownCommand() {
        CliCommand runCmd = stubCommand("run", 0);
        CliCommand listCmd = stubCommand("list", 0);
        CliCommand skillCmd = stubCommand("skill", 0);

        ReviewApp app = new ReviewApp(List.of(runCmd, listCmd, skillCmd), NULL_OUTPUT);
        int exit = app.execute(new String[]{"unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
    }

    @Test
    @DisplayName("doctorコマンドがレジストリ経由で呼び出される")
    void delegatesDoctorCommand() {
        AtomicInteger doctorCalled = new AtomicInteger();

        CliCommand doctorCmd = new CliCommand() {
            @Override
            public String name() { return "doctor"; }

            @Override
            public int execute(String[] args) {
                doctorCalled.incrementAndGet();
                return ExitCodes.OK;
            }
        };
        CliCommand runCmd = stubCommand("run", 0);

        ReviewApp app = new ReviewApp(List.of(runCmd, doctorCmd), NULL_OUTPUT);
        int exit = app.execute(new String[]{"doctor"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(doctorCalled.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("コマンドが動的に登録される（拡張性の検証）")
    void supportsExtensibleCommandRegistration() {
        CliCommand customCmd = new CliCommand() {
            @Override
            public String name() { return "custom"; }

            @Override
            public int execute(String[] args) { return 99; }
        };

        ReviewApp app = new ReviewApp(List.of(customCmd), NULL_OUTPUT);
        int exit = app.execute(new String[]{"custom"});

        assertThat(exit).isEqualTo(99);
    }

    @Test
    @DisplayName("危険なJVMフラグを検出する")
    void detectsInsecureJvmFlags() {
        List<String> detected = ReviewApp.detectInsecureJvmFlags(List.of(
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:OnOutOfMemoryError=/tmp/hook.sh"
        ));

        assertThat(detected).containsExactly("HeapDumpOnOutOfMemoryError", "OnOutOfMemoryError");
    }

    @Test
    @DisplayName("明示的に無効化されたヒープダンプフラグは検出しない")
    void ignoresExplicitlyDisabledHeapDumpFlag() {
        List<String> detected = ReviewApp.detectInsecureJvmFlags(List.of("-XX:-HeapDumpOnOutOfMemoryError"));

        assertThat(detected).doesNotContain("HeapDumpOnOutOfMemoryError");
    }

    private static CliCommand stubCommand(String name, int exitCode) {
        return new CliCommand() {
            @Override
            public String name() { return name; }

            @Override
            public int execute(String[] args) { return exitCode; }
        };
    }
}
