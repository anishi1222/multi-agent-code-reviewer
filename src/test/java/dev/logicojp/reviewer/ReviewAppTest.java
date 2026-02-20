package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliOutput;
import dev.logicojp.reviewer.cli.ExitCodes;
import dev.logicojp.reviewer.cli.ListAgentsCommand;
import dev.logicojp.reviewer.cli.ReviewCommand;
import dev.logicojp.reviewer.cli.SkillCommand;
import dev.logicojp.reviewer.cli.TestCliOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewApp")
class ReviewAppTest {

    private ByteArrayOutputStream outStream;
    private ByteArrayOutputStream errStream;
    private CliOutput output;
    private ReviewApp app;

    // Stub commands that override execute to return OK
    private static class StubReviewCommand extends ReviewCommand {
        StubReviewCommand(CliOutput output) {
            super(null, null, null, null, null, null, null, null, output);
        }

        @Override
        public int execute(String[] args) {
            return ExitCodes.OK;
        }
    }

    private static class StubListAgentsCommand extends ListAgentsCommand {
        StubListAgentsCommand(CliOutput output) {
            super(null, output);
        }

        @Override
        public int execute(String[] args) {
            return ExitCodes.OK;
        }
    }

    private static class StubSkillCommand extends SkillCommand {
        StubSkillCommand(CliOutput output) {
            super(null, null, null, null, null, output);
        }

        @Override
        public int execute(String[] args) {
            return ExitCodes.OK;
        }
    }

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        errStream = new ByteArrayOutputStream();
        output = TestCliOutput.create(
                new PrintStream(outStream, true, StandardCharsets.UTF_8),
                new PrintStream(errStream, true, StandardCharsets.UTF_8));
        app = new ReviewApp(
                new StubReviewCommand(output),
                new StubListAgentsCommand(output),
                new StubSkillCommand(output),
                output);
    }

    private String stdout() {
        return outStream.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return errStream.toString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("引数なし")
    class NoArgsTest {

        @Test
        @DisplayName("USAGEを返し、ヘルプを表示する")
        void returnsUsage() {
            int code = app.execute(new String[]{});
            assertThat(code).isEqualTo(ExitCodes.USAGE);
            assertThat(stdout()).contains("Usage:");
        }

        @Test
        @DisplayName("nullの場合はUSAGEを返す")
        void nullArgs() {
            int code = app.execute(null);
            assertThat(code).isEqualTo(ExitCodes.USAGE);
        }
    }

    @Nested
    @DisplayName("サブコマンド")
    class SubcommandTest {

        @Test
        @DisplayName("runコマンドを実行する")
        void runCommand() {
            int code = app.execute(new String[]{"run"});
            assertThat(code).isEqualTo(ExitCodes.OK);
        }

        @Test
        @DisplayName("listコマンドを実行する")
        void listCommand() {
            int code = app.execute(new String[]{"list"});
            assertThat(code).isEqualTo(ExitCodes.OK);
        }

        @Test
        @DisplayName("skillコマンドを実行する")
        void skillCommand() {
            int code = app.execute(new String[]{"skill"});
            assertThat(code).isEqualTo(ExitCodes.OK);
        }

        @Test
        @DisplayName("不明なコマンドはUSAGEを返す")
        void unknownCommand() {
            int code = app.execute(new String[]{"unknown"});
            assertThat(code).isEqualTo(ExitCodes.USAGE);
            assertThat(stderr()).contains("Unknown command");
        }
    }

    @Nested
    @DisplayName("グローバルオプション")
    class GlobalOptionsTest {

        @Test
        @DisplayName("--versionでバージョン情報を表示する")
        void versionFlag() {
            int code = app.execute(new String[]{"--version"});
            assertThat(code).isEqualTo(ExitCodes.OK);
            assertThat(stdout()).contains("Multi-Agent Reviewer");
        }

        @Test
        @DisplayName("-Vでバージョン情報を表示する")
        void shortVersionFlag() {
            int code = app.execute(new String[]{"-V"});
            assertThat(code).isEqualTo(ExitCodes.OK);
            assertThat(stdout()).contains("Multi-Agent Reviewer");
        }

        @Test
        @DisplayName("--helpのみでヘルプを表示する")
        void helpFlagOnly() {
            int code = app.execute(new String[]{"--help"});
            assertThat(code).isEqualTo(ExitCodes.OK);
            assertThat(stdout()).contains("Usage:");
        }

        @Test
        @DisplayName("--verboseフラグがサブコマンドに影響しない")
        void verboseWithSubcommand() {
            int code = app.execute(new String[]{"--verbose", "run"});
            assertThat(code).isEqualTo(ExitCodes.OK);
        }
    }

    @Nested
    @DisplayName("reviewプレフィックス")
    class ReviewPrefixTest {

        @Test
        @DisplayName("'review run'が動作する")
        void reviewRunPrefix() {
            int code = app.execute(new String[]{"review", "run"});
            assertThat(code).isEqualTo(ExitCodes.OK);
        }

        @Test
        @DisplayName("'review'のみはUSAGEを返す")
        void reviewOnly() {
            int code = app.execute(new String[]{"review"});
            assertThat(code).isEqualTo(ExitCodes.USAGE);
        }
    }
}
