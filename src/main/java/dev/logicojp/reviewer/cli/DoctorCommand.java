package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.CopilotCliHealthChecker;
import dev.logicojp.reviewer.service.CopilotCliPathResolver;
import dev.logicojp.reviewer.service.CopilotTimeoutResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/// Self-service runtime diagnostics command.
///
/// Verifies that all external dependencies (Copilot CLI, authentication)
/// and configuration are healthy, reporting granular pass/fail status
/// for each check. Use this to diagnose startup or authentication failures.
@Singleton
public class DoctorCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(DoctorCommand.class);

    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotCliHealthChecker cliHealthChecker;
    private final CopilotTimeoutResolver timeoutResolver;
    private final CliOutput output;

    @Inject
    public DoctorCommand(CopilotCliPathResolver cliPathResolver,
                         CopilotCliHealthChecker cliHealthChecker,
                         CopilotTimeoutResolver timeoutResolver,
                         CliOutput output) {
        this.cliPathResolver = cliPathResolver;
        this.cliHealthChecker = cliHealthChecker;
        this.timeoutResolver = timeoutResolver;
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

    private java.util.Optional<DoctorOptions> parseArgs(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    CliUsage.printDoctor(output);
                    return java.util.Optional.empty();
                }
                if (arg.startsWith("-")) {
                    throw new CliValidationException("Unknown option: " + arg, true);
                }
            }
        }
        return java.util.Optional.of(DoctorOptions.DEFAULT);
    }

    private int executeInternal(DoctorOptions options) {
        List<CheckResult> results = new ArrayList<>();

        printHeader();
        printJavaRuntime();
        printSeparator();

        String cliPath = checkCliPath(results);
        if (cliPath != null) {
            checkCliVersion(cliPath, results);
            checkCliAuth(cliPath, results);
        }
        printSeparator();

        printConfiguration();
        printSeparator();

        return printSummary(results);
    }

    private void printHeader() {
        output.println("review doctor");
        output.println("=============");
        output.println("");
    }

    private void printJavaRuntime() {
        output.println("Runtime Environment");
        output.println("  Java:    " + System.getProperty("java.version")
            + " (" + System.getProperty("java.vendor", "unknown") + ")");
        output.println("  OS:      " + System.getProperty("os.name", "unknown")
            + " " + System.getProperty("os.arch", "unknown"));
    }

    private String checkCliPath(List<CheckResult> results) {
        output.println("Copilot CLI");
        try {
            String path = cliPathResolver.resolveCliPath();
            output.println("  Path:    " + path + "  \u2713");
            results.add(CheckResult.pass("Copilot CLI path"));
            return path;
        } catch (CopilotCliException e) {
            output.println("  Path:    NOT FOUND  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            results.add(CheckResult.fail("Copilot CLI path", e.getMessage()));
            return null;
        }
    }

    private void checkCliVersion(String cliPath, List<CheckResult> results) {
        try {
            cliHealthChecker.checkCliVersion(cliPath);
            output.println("  Health:  OK  \u2713");
            results.add(CheckResult.pass("CLI version/health"));
        } catch (CopilotCliException e) {
            output.println("  Health:  FAILED  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            results.add(CheckResult.fail("CLI version/health", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.println("  Health:  INTERRUPTED  \u2717");
            results.add(CheckResult.fail("CLI version/health", "Interrupted"));
        }
    }

    private void checkCliAuth(String cliPath, List<CheckResult> results) {
        try {
            boolean supported = cliHealthChecker.checkCliAuth(cliPath);
            if (supported) {
                output.println("  Auth:    OK  \u2713");
                results.add(CheckResult.pass("CLI authentication"));
            } else {
                output.println("  Auth:    SKIPPED (auth status not supported by this CLI version)");
                results.add(CheckResult.pass("CLI authentication (skipped)"));
            }
        } catch (CopilotCliException e) {
            output.println("  Auth:    FAILED  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            output.println("  \u26a0 Run `github-copilot auth login` or `gh auth login` to authenticate.");
            results.add(CheckResult.fail("CLI authentication", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.println("  Auth:    INTERRUPTED  \u2717");
            results.add(CheckResult.fail("CLI authentication", "Interrupted"));
        }
    }

    private void printConfiguration() {
        output.println("Configuration");
        output.println("  Start timeout:       " + timeoutResolver.resolveStartTimeoutSeconds() + "s");
        output.println("  Healthcheck timeout: " + timeoutResolver.resolveCliHealthcheckSeconds() + "s");
        output.println("  Auth check timeout:  " + timeoutResolver.resolveCliAuthcheckSeconds() + "s");
    }

    private void printSeparator() {
        output.println("");
    }

    private int printSummary(List<CheckResult> results) {
        long failures = results.stream().filter(r -> !r.passed).count();
        if (failures == 0) {
            output.println("All checks passed. (" + results.size() + " checks)");
            return ExitCodes.OK;
        }
        output.errorln(failures + " issue(s) found:");
        for (CheckResult result : results) {
            if (!result.passed) {
                output.errorln("  \u2717 " + result.name + ": " + result.detail);
            }
        }
        return ExitCodes.UNAVAILABLE;
    }

    /// Represents the outcome of a single diagnostic check.
    record CheckResult(String name, boolean passed, String detail) {
        static CheckResult pass(String name) {
            return new CheckResult(name, true, null);
        }

        static CheckResult fail(String name, String detail) {
            return new CheckResult(name, false, detail);
        }
    }

    /// Placeholder options — doctor currently takes no arguments beyond --help.
    record DoctorOptions() {
        static final DoctorOptions DEFAULT = new DoctorOptions();
    }
}
