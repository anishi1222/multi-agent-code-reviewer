package dev.logicojp.reviewer.infrastructure.auth;

import dev.logicojp.reviewer.domain.resilience.CopilotCliException;
import dev.logicojp.reviewer.infrastructure.config.CopilotConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;

/// Resolves the filesystem path to the Copilot CLI binary.
@Singleton
public class CopilotCliPathResolver {

    public static final String CLI_PATH_ENV = "COPILOT_CLI_PATH";
    private static final String[] CLI_CANDIDATES = {"github-copilot", "copilot"};
    private final String configuredCliPath;
    private final String configuredPath;

    CopilotCliPathResolver() {
        this(null, CliPathResolver.systemPathValue());
    }

    @Inject
    public CopilotCliPathResolver(CopilotConfig copilotConfig) {
        this(copilotConfig.cliPath(), CliPathResolver.systemPathValue());
    }

    CopilotCliPathResolver(String configuredCliPath, String configuredPath) {
        this.configuredCliPath = configuredCliPath;
        this.configuredPath = configuredPath;
    }

    public String resolveCliPath() {
        String explicit = resolveExplicitCliPath();
        if (explicit != null) return explicit;
        return resolveCliPathFromSystemPath();
    }

    private String resolveExplicitCliPath() {
        String explicit = configuredCliPath;
        if (explicit == null || explicit.isBlank()) return null;
        var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, CLI_CANDIDATES);
        if (explicitPath.isPresent()) {
            if (!CliPathResolver.isInTrustedDirectory(explicitPath.get())) {
                throw new CopilotCliException("Copilot CLI at " + explicitPath.get()
                    + " is outside trusted directories. Set " + CLI_PATH_ENV
                    + " to a binary under /usr/bin, /usr/local/bin, /opt/homebrew/bin, or similar.");
            }
            return explicitPath.get().toString();
        }
        throw explicitPathNotFound(explicit);
    }

    private String resolveCliPathFromSystemPath() {
        String pathEnv = configuredPath;
        if (pathEnv == null || pathEnv.isBlank()) {
            throw new CopilotCliException("PATH is not set. Install GitHub Copilot CLI and/or set "
                + CLI_PATH_ENV + " to its executable path.");
        }
        var candidate = CliPathResolver.findTrustedExecutableInPathValue(pathEnv, CLI_CANDIDATES);
        if (candidate.isPresent()) return candidate.get().toString();
        throw new CopilotCliException("GitHub Copilot CLI not found in trusted PATH directories. "
            + "Install it or set " + CLI_PATH_ENV + ".");
    }

    private CopilotCliException explicitPathNotFound(String explicit) {
        Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
        return new CopilotCliException("Copilot CLI not found at " + explicitPathValue
            + ". Verify " + CLI_PATH_ENV + " or install GitHub Copilot CLI.");
    }
}
