package dev.logicojp.reviewer.cli;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;


@DisplayName("ListAgentsCommand")
class ListAgentsCommandTest {

    private static ListAgentsCommand createCommand(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        var output = TestCliOutput.create(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        return new ListAgentsCommand(null, output);
    }

    @Test
    @DisplayName("--help は OK を返して usage を表示する")
    void helpReturnsOk() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var command = createCommand(out, err);

        int exitCode = command.execute(new String[]{"--help"});

        Assertions.assertThat(exitCode).isEqualTo(ExitCodes.OK);
        Assertions.assertThat(out.toString(StandardCharsets.UTF_8)).contains("Usage: review list");
        Assertions.assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    @DisplayName("未知のオプションは USAGE を返す")
    void unknownOptionReturnsUsage() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var command = createCommand(out, err);

        int exitCode = command.execute(new String[]{"--unknown"});

        Assertions.assertThat(exitCode).isEqualTo(ExitCodes.USAGE);
        Assertions.assertThat(err.toString(StandardCharsets.UTF_8)).contains("Unknown option: --unknown");
        Assertions.assertThat(out.toString(StandardCharsets.UTF_8)).contains("Usage: review list");
    }
}
