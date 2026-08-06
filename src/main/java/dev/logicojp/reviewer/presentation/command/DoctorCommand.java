package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.DiagnosticResult;
import dev.logicojp.reviewer.application.port.inbound.RunDiagnosticsPort;
import dev.logicojp.reviewer.presentation.CliCommand;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.CliUsage;
import dev.logicojp.reviewer.presentation.CliValidationException;
import dev.logicojp.reviewer.presentation.ExitCodes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/// Self-service runtime diagnostics command.
///
/// Delegates all check execution to {@link RunDiagnosticsPort} and displays results.
/// No direct Copilot SDK or infrastructure imports.
@Singleton
public class DoctorCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(DoctorCommand.class);

    private final RunDiagnosticsPort runDiagnosticsPort;
    private final CliOutput output;

    @Inject
    public DoctorCommand(RunDiagnosticsPort runDiagnosticsPort, CliOutput output) {
        this.runDiagnosticsPort = runDiagnosticsPort;
        this.output = output;
    }

    @Override
    public String name() {
        return "doctor";
    }

    @Override
    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printDoctor,
            logger,
            output
        );
    }

    private Optional<DoctorOptions> parseArgs(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    CliUsage.printDoctor(output);
                    return Optional.empty();
                }
                if (arg.startsWith("-")) {
                    throw new CliValidationException("Unknown option: " + arg, true);
                }
            }
        }
        return Optional.of(DoctorOptions.DEFAULT);
    }

    private int executeInternal(DoctorOptions options) {
        printHeader();

        List<DiagnosticResult> results = runDiagnosticsPort.runAll();

        printSeparator();
        return printSummary(results);
    }

    private void printHeader() {
        output.println("review doctor");
        output.println("=============");
        output.println("");
        output.println("Runtime Environment");
        output.println("  Java:    " + System.getProperty("java.version")
            + " (" + System.getProperty("java.vendor", "unknown") + ")");
        output.println("  OS:      " + System.getProperty("os.name", "unknown")
            + " " + System.getProperty("os.arch", "unknown"));
        printSeparator();

        output.println("Diagnostic Results");
    }

    private void printSeparator() {
        output.println("");
    }

    private int printSummary(List<DiagnosticResult> results) {
        for (DiagnosticResult result : results) {
            String icon = result.passed() ? "\u2713" : "\u2717";
            output.println("  " + icon + " " + result.checkName() + ": " + result.message());
            if (!result.detail().isBlank()) {
                output.println("      " + result.detail());
            }
        }

        printSeparator();
        long failures = results.stream().filter(r -> !r.passed()).count();
        if (failures == 0) {
            output.println("All checks passed. (" + results.size() + " checks)");
            return ExitCodes.OK;
        }
        output.errorln(failures + " issue(s) found.");
        return ExitCodes.UNAVAILABLE;
    }

    record DoctorOptions() {
        static final DoctorOptions DEFAULT = new DoctorOptions();
    }
}
