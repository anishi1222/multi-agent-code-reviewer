package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CliUsage")
class CliUsageTest {

    private CliOutput createOutput(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return new CliOutput(new PrintStream(out), new PrintStream(err));
    }

    @Nested
    @DisplayName("printGeneral")
    class PrintGeneral {

        @Test
        @DisplayName("stdoutに使用法を出力する")
        void printsToStdout() {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, err);

            CliUsage.printGeneral(output);

            assertThat(out.toString()).contains("Usage: review <command> [options]");
            assertThat(err.toString()).isEmpty();
        }

        @Test
        @DisplayName("コマンド一覧を含む")
        void containsCommands() {
            var out = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, new ByteArrayOutputStream());

            CliUsage.printGeneral(output);

            String text = out.toString();
            assertThat(text).contains("run");
            assertThat(text).contains("list");
            assertThat(text).contains("skill");
        }
    }

    @Nested
    @DisplayName("printGeneralError")
    class PrintGeneralError {

        @Test
        @DisplayName("stderrに使用法を出力する")
        void printsToStderr() {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, err);

            CliUsage.printGeneralError(output);

            assertThat(err.toString()).contains("Usage: review <command> [options]");
            assertThat(out.toString()).isEmpty();
        }

        @Test
        @DisplayName("printGeneralと同じ内容を出力する")
        void sameContentAsGeneral() {
            var out1 = new ByteArrayOutputStream();
            var err1 = new ByteArrayOutputStream();
            CliUsage.printGeneral(createOutput(out1, err1));

            var out2 = new ByteArrayOutputStream();
            var err2 = new ByteArrayOutputStream();
            CliUsage.printGeneralError(createOutput(out2, err2));

            assertThat(err2.toString()).isEqualTo(out1.toString());
        }
    }

    @Nested
    @DisplayName("printRun")
    class PrintRun {

        @Test
        @DisplayName("runコマンドの使用法を出力する")
        void printsRunUsage() {
            var out = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, new ByteArrayOutputStream());

            CliUsage.printRun(output);

            String text = out.toString();
            assertThat(text).contains("review run");
            assertThat(text).contains("--repo");
            assertThat(text).contains("--local");
            assertThat(text).contains("--all");
            assertThat(text).contains("--agents");
        }
    }

    @Nested
    @DisplayName("printList")
    class PrintList {

        @Test
        @DisplayName("listコマンドの使用法を出力する")
        void printsListUsage() {
            var out = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, new ByteArrayOutputStream());

            CliUsage.printList(output);

            assertThat(out.toString()).contains("review list");
        }
    }

    @Nested
    @DisplayName("printSkill")
    class PrintSkill {

        @Test
        @DisplayName("skillコマンドの使用法を出力する")
        void printsSkillUsage() {
            var out = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, new ByteArrayOutputStream());

            CliUsage.printSkill(output);

            String text = out.toString();
            assertThat(text).contains("review skill");
            assertThat(text).contains("--param");
        }
    }

    @Nested
    @DisplayName("printDoctor")
    class PrintDoctor {

        @Test
        @DisplayName("doctorコマンドの使用法を出力する")
        void printsDoctorUsage() {
            var out = new ByteArrayOutputStream();
            CliOutput output = createOutput(out, new ByteArrayOutputStream());

            CliUsage.printDoctor(output);

            String text = out.toString();
            assertThat(text).contains("review doctor");
            assertThat(text).contains("runtime dependencies");
        }
    }
}
