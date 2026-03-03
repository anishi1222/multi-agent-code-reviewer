package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliOutput;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.cli.ListAgentsCommand;
import dev.logicojp.reviewer.cli.ReviewCommand;
import dev.logicojp.reviewer.cli.SkillCommand;
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

        ReviewCommand reviewCommand = new ReviewCommand(null, null, null, null, null, null, null, null, NULL_OUTPUT) {
            @Override
            public int execute(String[] args) {
                runCalled.incrementAndGet();
                return 42;
            }
        };
        ListAgentsCommand listCommand = new ListAgentsCommand(null, NULL_OUTPUT) {
            @Override
            public int execute(String[] args) {
                return 0;
            }
        };
        SkillCommand skillCommand = new SkillCommand(null, null, null, null, null, null, NULL_OUTPUT) {
            @Override
            public int execute(String[] args) {
                return 0;
            }
        };

        ReviewApp app = new ReviewApp(reviewCommand, listCommand, skillCommand, NULL_OUTPUT);
        int exit = app.execute(new String[]{"run"});

        assertThat(exit).isEqualTo(42);
        assertThat(runCalled.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("未知コマンドではUSAGEを返す")
    void returnsUsageForUnknownCommand() {
        ReviewCommand reviewCommand = new ReviewCommand(null, null, null, null, null, null, null, null, NULL_OUTPUT) {
            @Override
            public int execute(String[] args) {
                return 0;
            }
        };
        ListAgentsCommand listCommand = new ListAgentsCommand(null, NULL_OUTPUT) {
            @Override
            public int execute(String[] args) {
                return 0;
            }
        };
        SkillCommand skillCommand = new SkillCommand(null, null, null, null, null, null, NULL_OUTPUT) {
            @Override
            public int execute(String[] args) {
                return 0;
            }
        };

        ReviewApp app = new ReviewApp(reviewCommand, listCommand, skillCommand, NULL_OUTPUT);
        int exit = app.execute(new String[]{"unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
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
}
