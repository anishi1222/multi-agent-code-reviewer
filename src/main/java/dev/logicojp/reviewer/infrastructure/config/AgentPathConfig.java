package dev.logicojp.reviewer.infrastructure.config;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

/// Configuration for default agent directories.
@ConfigurationProperties("reviewer.agents")
public record AgentPathConfig(List<String> directories) {

    public static final List<String> DEFAULT_DIRECTORIES = List.of("./agents", "./.github/agents");

    public AgentPathConfig {
        directories = normalizeDirectories(directories);
    }

    private static List<String> normalizeDirectories(List<String> directories) {
        return directories == null || directories.isEmpty()
            ? DEFAULT_DIRECTORIES
            : List.copyOf(directories);
    }
}
