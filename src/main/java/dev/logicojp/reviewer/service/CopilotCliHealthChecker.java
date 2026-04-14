package dev.logicojp.reviewer.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import dev.logicojp.reviewer.util.CliPathResolver;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// Verifies the health and authentication status of the Copilot CLI binary.
@Singleton
public class CopilotCliHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(CopilotCliHealthChecker.class);
    private static final int MAX_CLI_CHECK_ATTEMPTS = 2;
    private static final long CLI_BACKOFF_BASE_MS = 1_000L;
    private static final long CLI_BACKOFF_MAX_MS = 3_000L;
    private static final String[] CLI_CANDIDATES = {"github-copilot", "copilot"};
    private static final Path SAFE_WORKING_DIRECTORY =
        Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

    private final CopilotTimeoutResolver timeoutResolver;

    @Inject
    public CopilotCliHealthChecker(CopilotTimeoutResolver timeoutResolver) {
        this.timeoutResolver = timeoutResolver;
    }

    public void verifyCliHealthy(String cliPath) throws InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        checkCliVersion(cliPath);

        if (!checkCliAuth(cliPath)) {
            logger.info("Copilot CLI does not support 'auth status' subcommand (v1.0.25+). "
                + "Skipping auth pre-check; the SDK will verify authentication at session creation.");
        }
    }

    /// Runs only the version/health check against the CLI binary.
    /// Throws {@link CopilotCliException} if the CLI does not respond or exits non-zero.
    public void checkCliVersion(String cliPath) throws InterruptedException {
        runCliCommand(versionCommand(cliPath), resolveCliHealthcheckSeconds(),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ",
            "Ensure the CLI is installed and authenticated.");
    }

    /// Checks the CLI authentication status.
    /// Returns {@code true} if the auth check passed, {@code false} if the CLI
    /// does not support the command (e.g., v1.0.25+).
    /// Throws {@link CopilotCliException} for definitive auth failures.
    public boolean checkCliAuth(String cliPath) throws InterruptedException {
        return tryAuthStatusCheck(cliPath);
    }

    /// Attempts the auth status check; returns true if the check passed, false if the CLI
    /// does not support the command (exit 1 with "Invalid command format").
    private boolean tryAuthStatusCheck(String cliPath) throws InterruptedException {
        try {
            runCliCommand(authStatusCommand(cliPath), resolveCliAuthcheckSeconds(),
                "Copilot CLI auth status timed out after ",
                "Copilot CLI auth status failed with code ",
                "Failed to execute Copilot CLI auth status: ",
                "Run `copilot login` (or `gh copilot -- login`) to authenticate.");
            return true;
        } catch (CopilotCliException e) {
            // CLI 1.0.25+ does not expose "auth status" — treat as non-fatal
            if (e.getMessage() != null && e.getMessage().contains("failed with code 1")) {
                return false;
            }
            throw e;
        }
    }

    private List<String> versionCommand(String cliPath) {
        return List.of(cliPath, "--version");
    }

    private List<String> authStatusCommand(String cliPath) {
        return List.of(cliPath, "auth", "status");
    }

    private void runCliCommand(List<String> command, long timeoutSeconds,
                               String timeoutMessage, String exitMessage, String ioMessage,
                               String remediationMessage)
        throws InterruptedException {
        CopilotCliException lastException = null;
        for (int attempt = 1; attempt <= MAX_CLI_CHECK_ATTEMPTS; attempt++) {
            try {
                runCliCommandOnce(command, timeoutSeconds, timeoutMessage, exitMessage, ioMessage, remediationMessage);
                return;
            } catch (CopilotCliException e) {
                lastException = e;
                if (attempt >= MAX_CLI_CHECK_ATTEMPTS) {
                    throw e;
                }
                long backoffMs = RetryPolicyUtils.computeBackoffWithJitter(
                    CLI_BACKOFF_BASE_MS,
                    CLI_BACKOFF_MAX_MS,
                    attempt
                );
                logger.warn("CLI health check failed (attempt {}/{}), retrying in {}ms: {}",
                    attempt, MAX_CLI_CHECK_ATTEMPTS, backoffMs, e.getMessage());
                Thread.sleep(backoffMs);
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }

    private void runCliCommandOnce(List<String> command, long timeoutSeconds,
                                   String timeoutMessage, String exitMessage, String ioMessage,
                                   String remediationMessage) {
        List<String> validatedCommand = withRevalidatedExecutable(command);
        ProcessBuilder builder = new ProcessBuilder(validatedCommand);
        builder.directory(SAFE_WORKING_DIRECTORY.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            var drainThread = Thread.ofVirtual().name("cli-drain").start(() -> {
                try (var in = process.getInputStream()) {
                    in.transferTo(OutputStream.nullOutputStream());
                } catch (IOException _) {
                }
            });
            try {
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    handleTimeout(process, drainThread, timeoutSeconds, timeoutMessage, remediationMessage);
                }
                if (process.exitValue() != 0) {
                    throw exitFailure(exitMessage, process.exitValue(), remediationMessage);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CopilotCliException("Interrupted while waiting for CLI command", e);
            } finally {
                try {
                    drainThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CopilotCliException("Interrupted while draining CLI output", e);
                }
            }
        } catch (IOException e) {
            throw new CopilotCliException(ioMessage + e.getMessage(), e);
        }
    }

    private List<String> withRevalidatedExecutable(List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new CopilotCliException("CLI command is empty");
        }
        String executable = command.getFirst();
        Path runtimePath = CliPathResolver.revalidateExecutionPath(executable, CLI_CANDIDATES)
            .orElseThrow(() -> new CopilotCliException(
                "Copilot CLI path failed execution-time validation. "
                    + "The binary may have changed after initial resolution."));
        List<String> validatedCommand = new ArrayList<>(command);
        validatedCommand.set(0, runtimePath.toString());
        return validatedCommand;
    }

    private void handleTimeout(Process process,
                               Thread drainThread,
                               long timeoutSeconds,
                               String timeoutMessage,
                               String remediationMessage) {
        process.destroyForcibly();
        drainThread.interrupt();
        throw new CopilotCliException(timeoutMessage + timeoutSeconds + "s. " + remediationMessage);
    }

    private CopilotCliException exitFailure(String exitMessage, int exitCode, String remediationMessage) {
        String baseMessage = exitMessage + exitCode + ". ";
        return new CopilotCliException(baseMessage + remediationMessage);
    }

    private long resolveCliHealthcheckSeconds() {
        return timeoutResolver.resolveCliHealthcheckSeconds();
    }

    private long resolveCliAuthcheckSeconds() {
        return timeoutResolver.resolveCliAuthcheckSeconds();
    }
}
