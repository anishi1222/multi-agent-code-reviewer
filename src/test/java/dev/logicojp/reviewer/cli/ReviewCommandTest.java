package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewCommand")
class ReviewCommandTest {

    private static ExecutionConfig executionDefaults() {
        return new ExecutionConfig(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null,
            new ExecutionConfig.SummarySettings(0, 0, 0, 0, 0, 0));
    }

    private static ReviewCommand createCommand(CliOutput output) {
        return new ReviewCommand(
            new ModelConfig(),
            executionDefaults(),
            null,
            null,
            null,
            null,
            null,
            null,
            output);
    }

    @Test
    @DisplayName("--help は OK を返して usage を表示する")
    void helpReturnsOk() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var output = TestCliOutput.create(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        var command = createCommand(output);

        int exitCode = command.execute(new String[]{"--help"});

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Usage: review run");
    }

    @Test
    @DisplayName("--repo と --local の同時指定は USAGE を返す")
    void repoAndLocalAreMutuallyExclusive() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var output = TestCliOutput.create(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        var command = createCommand(output);

        int exitCode = command.execute(new String[]{
            "--repo", "owner/repo",
            "--local", ".",
            "--all"
        });

        assertThat(exitCode).isEqualTo(ExitCodes.USAGE);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Specify either --repo or --local");
    }

    @Test
    @DisplayName("--all と --agents の同時指定は USAGE を返す")
    void allAndAgentsAreMutuallyExclusive() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var output = TestCliOutput.create(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        var command = createCommand(output);

        int exitCode = command.execute(new String[]{
            "--repo", "owner/repo",
            "--all",
            "--agents", "security"
        });

        assertThat(exitCode).isEqualTo(ExitCodes.USAGE);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Specify either --all or --agents");
    }
}
