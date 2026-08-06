package dev.logicojp.reviewer.presentation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.PrintStream;

/// CLI output abstraction to avoid direct dependency on System.out/System.err in commands.
@Singleton
public class CliOutput {

    private final PrintStream out;
    private final PrintStream err;

    @Inject
    public CliOutput() {
        this(System.out, System.err);
    }

    public CliOutput(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    PrintStream out() {
        return out;
    }

    PrintStream err() {
        return err;
    }

    public void println(String message) {
        writeLine(out, message);
    }

    public void errorln(String message) {
        writeLine(err, message);
    }

    private void writeLine(PrintStream stream, String message) {
        stream.println(message);
    }
}
