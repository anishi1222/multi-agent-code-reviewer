package dev.logicojp.reviewer.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CliOutput")
class CliOutputTest {

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("カスタムストリームを使用できる")
        void usesCustomStreams() {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            var output = new CliOutput(new PrintStream(out), new PrintStream(err));

            assertThat(output.out()).isNotNull();
            assertThat(output.err()).isNotNull();
        }
    }

    @Nested
    @DisplayName("println")
    class Println {

        @Test
        @DisplayName("stdoutにメッセージを出力する")
        void printsToStdout() {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            var output = new CliOutput(new PrintStream(out), new PrintStream(err));

            output.println("hello");

            assertThat(out.toString()).contains("hello");
            assertThat(err.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("errorln")
    class Errorln {

        @Test
        @DisplayName("stderrにメッセージを出力する")
        void printsToStderr() {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            var output = new CliOutput(new PrintStream(out), new PrintStream(err));

            output.errorln("error message");

            assertThat(err.toString()).contains("error message");
            assertThat(out.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("out / err アクセサ")
    class Accessors {

        @Test
        @DisplayName("outは設定されたPrintStreamを返す")
        void outReturnsConfiguredStream() {
            var outStream = new PrintStream(new ByteArrayOutputStream());
            var errStream = new PrintStream(new ByteArrayOutputStream());
            var output = new CliOutput(outStream, errStream);

            assertThat(output.out()).isSameAs(outStream);
        }

        @Test
        @DisplayName("errは設定されたPrintStreamを返す")
        void errReturnsConfiguredStream() {
            var outStream = new PrintStream(new ByteArrayOutputStream());
            var errStream = new PrintStream(new ByteArrayOutputStream());
            var output = new CliOutput(outStream, errStream);

            assertThat(output.err()).isSameAs(errStream);
        }
    }
}
