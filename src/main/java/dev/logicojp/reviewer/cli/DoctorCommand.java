package dev.logicojp.reviewer.cli;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.GetAuthStatusResponse;
import com.github.copilot.sdk.json.GetStatusResponse;
import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.CopilotCliPathResolver;
import dev.logicojp.reviewer.service.CopilotHealthProbe;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.CopilotTimeoutResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/// Self-service runtime diagnostics command.
///
/// Verifies that all external dependencies (Copilot CLI binary on disk, SDK
/// connectivity, authentication) and configuration are healthy by exercising
/// the Copilot SDK directly rather than spawning CLI subprocesses. Reports
/// granular pass/fail status for each check.
@Singleton
public class DoctorCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(DoctorCommand.class);

    private final CopilotCliPathResolver cliPathResolver;
    private final CopilotService copilotService;
    private final CopilotHealthProbe healthProbe;
    private final CopilotTimeoutResolver timeoutResolver;
    private final CliOutput output;

    @Inject
    public DoctorCommand(CopilotCliPathResolver cliPathResolver,
                         CopilotService copilotService,
                         CopilotHealthProbe healthProbe,
                         CopilotTimeoutResolver timeoutResolver,
                         CliOutput output) {
        this.cliPathResolver = cliPathResolver;
        this.copilotService = copilotService;
        this.healthProbe = healthProbe;
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
        if (cliPath != null && checkClientInitialization(results)) {
            CopilotClient client = copilotService.getClient();
            checkConnectionState(client, results);
            checkSdkStatus(client, results);
            checkSdkAuthStatus(client, results);
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

    private boolean checkClientInitialization(List<CheckResult> results) {
        try {
            copilotService.initializeOrThrow();
            output.println("  Init:    OK  \u2713");
            results.add(CheckResult.pass("Copilot client initialization"));
            return true;
        } catch (CopilotCliException e) {
            output.println("  Init:    FAILED  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            results.add(CheckResult.fail("Copilot client initialization", e.getMessage()));
            return false;
        } catch (RuntimeException e) {
            output.println("  Init:    FAILED  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            results.add(CheckResult.fail("Copilot client initialization", e.getMessage()));
            return false;
        }
    }

    private void checkConnectionState(CopilotClient client, List<CheckResult> results) {
        ConnectionState state = healthProbe.getConnectionState(client);
        if (state == ConnectionState.CONNECTED) {
            output.println("  State:   CONNECTED  \u2713");
            results.add(CheckResult.pass("Copilot connection state"));
        } else {
            String label = state == null ? "UNKNOWN" : state.name();
            output.println("  State:   " + label + "  \u2717");
            results.add(CheckResult.fail("Copilot connection state", label));
        }
    }

    private void checkSdkStatus(CopilotClient client, List<CheckResult> results) {
        try {
            GetStatusResponse status = healthProbe.fetchStatus(client);
            output.println("  Status:  v" + nullableString(status.getVersion())
                + " (protocol " + status.getProtocolVersion() + ")  \u2713");
            results.add(CheckResult.pass("SDK status"));
        } catch (CopilotCliException e) {
            output.println("  Status:  FAILED  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            results.add(CheckResult.fail("SDK status", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.println("  Status:  INTERRUPTED  \u2717");
            results.add(CheckResult.fail("SDK status", "Interrupted"));
        }
    }

    private void checkSdkAuthStatus(CopilotClient client, List<CheckResult> results) {
        try {
            GetAuthStatusResponse auth = healthProbe.fetchAuthStatus(client);
            if (auth.isAuthenticated()) {
                String detail = describeAuthenticated(auth);
                output.println("  Auth:    " + detail + "  \u2713");
                results.add(CheckResult.pass("SDK authentication"));
            } else {
                String detail = describeUnauthenticated(auth);
                output.println("  Auth:    NOT AUTHENTICATED  \u2717");
                output.println("  \u26a0 " + detail);
                output.println("  \u26a0 Run `github-copilot auth login` or `gh auth login` to authenticate.");
                results.add(CheckResult.fail("SDK authentication", detail));
            }
        } catch (CopilotCliException e) {
            output.println("  Auth:    FAILED  \u2717");
            output.println("  \u26a0 " + e.getMessage());
            output.println("  \u26a0 Run `github-copilot auth login` or `gh auth login` to authenticate.");
            results.add(CheckResult.fail("SDK authentication", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.println("  Auth:    INTERRUPTED  \u2717");
            results.add(CheckResult.fail("SDK authentication", "Interrupted"));
        }
    }

    private String describeAuthenticated(GetAuthStatusResponse auth) {
        StringBuilder sb = new StringBuilder("OK");
        if (auth.getLogin() != null && !auth.getLogin().isBlank()) {
            sb.append(" (").append(auth.getLogin());
            if (auth.getHost() != null && !auth.getHost().isBlank()) {
                sb.append("@").append(auth.getHost());
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private String describeUnauthenticated(GetAuthStatusResponse auth) {
        if (auth.getStatusMessage() != null && !auth.getStatusMessage().isBlank()) {
            return auth.getStatusMessage();
        }
        return "Authentication required.";
    }

    private static String nullableString(String value) {
        return value == null ? "unknown" : value;
    }

    private void printConfiguration() {
        output.println("Configuration");
        output.println("  Start timeout:           " + timeoutResolver.resolveStartTimeoutSeconds() + "s");
        output.println("  SDK status timeout:      " + timeoutResolver.resolveSdkStatusTimeoutSeconds() + "s");
        output.println("  SDK auth-status timeout: " + timeoutResolver.resolveSdkAuthStatusTimeoutSeconds() + "s");
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
