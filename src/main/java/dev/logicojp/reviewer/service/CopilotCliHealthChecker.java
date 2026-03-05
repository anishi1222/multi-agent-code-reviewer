package dev.logicojp.reviewer.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// Verifies the health and authentication status of the Copilot CLI binary.
@Singleton
public class CopilotCliHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(CopilotCliHealthChecker.class);
    private static final long DEFAULT_CLI_HEALTHCHECK_SECONDS = 10;
    private static final int MAX_CLI_CHECK_ATTEMPTS = 2;
    private static final long CLI_BACKOFF_BASE_MS = 1_000L;
    private static final long CLI_BACKOFF_MAX_MS = 3_000L;
    private static final String CLI_HEALTHCHECK_ENV = "COPILOT_CLI_HEALTHCHECK_SECONDS";
    private static final String CLI_AUTH_CHECK_ENV = "COPILOT_CLI_AUTHCHECK_SECONDS";
    private static final long DEFAULT_CLI_AUTHCHECK_SECONDS = 15;
    private static final Path SAFE_WORKING_DIRECTORY =
        Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

    private final CopilotTimeoutResolver timeoutResolver;

    @Inject
    public CopilotCliHealthChecker(CopilotTimeoutResolver timeoutResolver) {
        this.timeoutResolver = timeoutResolver;
    }

    public void verifyCliHealthy(String cliPath, boolean tokenProvided) throws InterruptedException {
        if (cliPath == null || cliPath.isBlank()) {
            return;
        }
        runCliCommand(versionCommand(cliPath), resolveCliHealthcheckSeconds(),
            "Copilot CLI did not respond within ",
            "Copilot CLI exited with code ",
            "Failed to execute Copilot CLI: ",
            "Ensure the CLI is installed and authenticated.");

        if (tokenProvided) {
            logger.info("GITHUB_TOKEN provided — skipping CLI auth status check");
        } else {
            runCliCommand(authStatusCommand(cliPath), resolveCliAuthcheckSeconds(),
                "Copilot CLI auth status timed out after ",
                "Copilot CLI auth status failed with code ",
                "Failed to execute Copilot CLI auth status: ",
                "Run `github-copilot auth login` to authenticate.");
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
        ProcessBuilder builder = new ProcessBuilder(command);
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
        return timeoutResolver.resolveEnvTimeout(CLI_HEALTHCHECK_ENV, DEFAULT_CLI_HEALTHCHECK_SECONDS);
    }

    private long resolveCliAuthcheckSeconds() {
        return timeoutResolver.resolveEnvTimeout(CLI_AUTH_CHECK_ENV, DEFAULT_CLI_AUTHCHECK_SECONDS);
    }
}
