package dev.logicojp.reviewer.util;

import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

final class GhCliLocator {

    private static final Logger logger = LoggerFactory.getLogger(GhCliLocator.class);
    private static final String GH_CLI_PATH_ENV = "GH_CLI_PATH";

    private final String configuredGhCliPath;
    private final String configuredPath;

    GhCliLocator(@Nullable String configuredGhCliPath, @Nullable String configuredPath) {
        this.configuredGhCliPath = configuredGhCliPath;
        this.configuredPath = configuredPath;
    }

    @Nullable String resolve() {
        String explicit = configuredGhCliPath;
        if (explicit != null && !explicit.isBlank()) {
            var explicitPath = CliPathResolver.resolveExplicitExecutable(explicit, "gh");
            if (explicitPath.isPresent()) {
                if (!CliPathResolver.isInTrustedDirectory(explicitPath.get())) {
                    logger.warn("Rejected {} outside trusted directories: {}", GH_CLI_PATH_ENV, explicitPath.get());
                    return null;
                }
                return explicitPath.get().toString();
            }
            Path explicitPathValue = Path.of(explicit.trim()).toAbsolutePath().normalize();
            logger.warn("Invalid {} value: {}", GH_CLI_PATH_ENV, explicitPathValue);
            return null;
        }

        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        return CliPathResolver.findTrustedExecutableInPathValue(configuredPath, "gh")
            .map(path -> path.toAbsolutePath().normalize().toString())
            .orElse(null);
    }
}
