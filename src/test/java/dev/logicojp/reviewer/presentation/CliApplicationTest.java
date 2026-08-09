package dev.logicojp.reviewer.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CliApplication")
class CliApplicationTest {

    private static final CliOutput NULL_OUTPUT = new CliOutput(
        new PrintStream(OutputStream.nullOutputStream()),
        new PrintStream(OutputStream.nullOutputStream())
    );

    @Test
    @DisplayName("runサブコマンドをReviewCommandに委譲する")
    void delegatesRunCommand() {
        AtomicInteger runCalled = new AtomicInteger();
        CliCommand run = command("run", _ -> {
            runCalled.incrementAndGet();
            return 42;
        });

        int exit = application(List.of(run), NULL_OUTPUT).execute(new String[]{"run"});

        assertThat(exit).isEqualTo(42);
        assertThat(runCalled.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("未知コマンドではUSAGEを返す")
    void returnsUsageForUnknownCommand() {
        int exit = application(List.of(command("run", _ -> 0)), NULL_OUTPUT)
            .execute(new String[]{"unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
    }

    @Test
    @DisplayName("doctorコマンドがレジストリ経由で呼び出される")
    void delegatesDoctorCommand() {
        AtomicInteger doctorCalled = new AtomicInteger();
        CliCommand doctor = command("doctor", _ -> {
            doctorCalled.incrementAndGet();
            return ExitCodes.OK;
        });

        int exit = application(List.of(doctor), NULL_OUTPUT).execute(new String[]{"doctor"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(doctorCalled.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("コマンドが動的に登録される（拡張性の検証）")
    void supportsExtensibleCommandRegistration() {
        int exit = application(List.of(command("custom", _ -> 99)), NULL_OUTPUT)
            .execute(new String[]{"custom"});

        assertThat(exit).isEqualTo(99);
    }

    @Test
    @DisplayName("reviewプレフィックス後のサブコマンドを委譲する")
    void supportsReviewPrefix() {
        AtomicInteger argumentCount = new AtomicInteger();
        CliCommand run = command("run", args -> {
            argumentCount.set(args.length);
            return ExitCodes.OK;
        });

        int exit = application(List.of(run), NULL_OUTPUT)
            .execute(new String[]{"review", "run", "--dry-run"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(argumentCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("verbose指定はinboundポート経由でログ設定を委譲する")
    void delegatesVerboseLoggingThroughInboundPort() {
        AtomicInteger configured = new AtomicInteger();
        var app = new CliApplication(
            List.of(command("run", _ -> ExitCodes.OK)),
            NULL_OUTPUT,
            () -> {
                configured.incrementAndGet();
                return true;
            });

        assertThat(app.execute(new String[]{"--verbose", "run"})).isEqualTo(ExitCodes.OK);
        assertThat(configured.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ログ設定失敗はstderrへ表示してコマンドを継続する")
    void reportsLoggingFailureAndContinues() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(
            new PrintStream(OutputStream.nullOutputStream()),
            new PrintStream(error));
        var app = new CliApplication(
            List.of(command("run", _ -> ExitCodes.OK)),
            output,
            () -> false);

        assertThat(app.execute(new String[]{"--verbose", "run"})).isEqualTo(ExitCodes.OK);
        assertThat(error.toString()).contains("Failed to enable verbose logging");
    }

    @Test
    @DisplayName("version指定はコマンドを実行せずバージョンを表示する")
    void printsVersionWithoutDispatching() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        AtomicInteger calls = new AtomicInteger();
        CliOutput output = new CliOutput(
            new PrintStream(outputBytes),
            new PrintStream(OutputStream.nullOutputStream()));
        var app = application(
            List.of(command("run", _ -> {
                calls.incrementAndGet();
                return ExitCodes.OK;
            })),
            output);

        assertThat(app.execute(new String[]{"--version", "run"})).isEqualTo(ExitCodes.OK);
        assertThat(outputBytes.toString()).contains("Multi-Agent Reviewer");
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("同名コマンドの二重登録を拒否する")
    void rejectsDuplicateCommandNames() {
        assertThatThrownBy(() -> application(
            List.of(command("run", _ -> 0), command("run", _ -> 1)),
            NULL_OUTPUT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate CLI command name: run");
    }

    private static CliApplication application(List<CliCommand> commands, CliOutput output) {
        return new CliApplication(commands, output, () -> true);
    }

    private static CliCommand command(String name, CommandBody body) {
        return new CliCommand() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int execute(String[] args) {
                return body.execute(args);
            }
        };
    }

    @FunctionalInterface
    private interface CommandBody {
        int execute(String[] args);
    }
}
