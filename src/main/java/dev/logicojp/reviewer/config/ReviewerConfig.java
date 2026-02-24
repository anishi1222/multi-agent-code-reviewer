package dev.logicojp.reviewer.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/// Unified configuration for agent paths, local file collection, and skill settings.
/// Merges the former AgentPathConfig, LocalFileConfig, and SkillConfig into nested records
/// under the `reviewer` YAML prefix.
@ConfigurationProperties("reviewer")
public record ReviewerConfig(
    AgentPaths agents,
    LocalFiles localFiles,
    Skills skills
) {

    public ReviewerConfig {
        agents = agents != null ? agents : new AgentPaths(null);
        localFiles = localFiles != null ? localFiles : new LocalFiles(0, 0, null, null, null, null);
        skills = skills != null ? skills : new Skills(null, null, 0, 0, 0, 0, 0);
    }

    // --- Agent path configuration ---

    /// Configuration for default agent directories.
    /// Bound to `reviewer.agents` in YAML.
    @ConfigurationProperties("agents")
    public record AgentPaths(List<String> directories) {

        public static final List<String> DEFAULT_DIRECTORIES = List.of("./agents", "./.github/agents");

        public AgentPaths {
            directories = (directories == null || directories.isEmpty())
                ? DEFAULT_DIRECTORIES
                : List.copyOf(directories);
        }
    }

    // --- Local file collection configuration ---

    /// Configuration for local file collection limits and filtering.
    /// Bound to `reviewer.local-files` in YAML.
    @ConfigurationProperties("local-files")
    public record LocalFiles(
        long maxFileSize,
        long maxTotalSize,
        @Nullable List<String> ignoredDirectories,
        @Nullable List<String> sourceExtensions,
        @Nullable List<String> sensitiveFilePatterns,
        @Nullable List<String> sensitiveExtensions
    ) {

        public static final long DEFAULT_MAX_FILE_SIZE = 256 * 1024;
        public static final long DEFAULT_MAX_TOTAL_SIZE = 2 * 1024 * 1024;

        private static final List<String> FALLBACK_IGNORED_DIRECTORIES = List.of(
            ".git", ".svn", ".hg",
            "node_modules", "bower_components",
            "target", "build", "out", "dist", "bin", "obj",
            ".gradle", ".mvn", ".idea", ".vscode", ".vs",
            "__pycache__", ".mypy_cache", ".pytest_cache",
            "vendor", ".bundle",
            "coverage", ".nyc_output",
            ".terraform"
        );

        private static final List<String> FALLBACK_SOURCE_EXTENSIONS = List.of(
            "java", "kt", "kts", "groovy", "scala", "clj",
            "js", "jsx", "ts", "tsx", "mjs", "cjs", "vue", "svelte",
            "c", "cpp", "cc", "cxx", "h", "hpp", "rs", "go", "zig",
            "py", "rb", "php", "pl", "pm", "lua", "r",
            "sh", "bash", "zsh", "fish", "ps1", "psm1",
            "cs", "fs", "vb",
            "swift", "m", "mm",
            "sql", "graphql", "gql", "proto",
            "yaml", "yml", "json", "toml", "xml", "properties",
            "gradle", "cmake", "makefile",
            "md", "rst", "adoc"
        );

        private static final List<String> FALLBACK_SENSITIVE_FILE_PATTERNS = List.of(
            "application-prod", "application-staging", "application-secret",
            "application-local", "application-dev", "application-ci",
            "secrets", "credentials", ".env",
            ".env.local", ".env.production", ".env.development",
            ".env.staging", ".env.test",
            "service-account", "keystore", "truststore",
            "id_rsa", "id_ed25519", "id_ecdsa",
            ".netrc", ".npmrc", ".pypirc", ".docker/config",
            "vault-config", "aws-credentials",
            "terraform.tfvars", "kubeconfig", ".kube/config",
            "htpasswd", "shadow",
            "application.yml", "application.yaml",
            "config.json", "settings.json", "settings.yaml"
        );

        private static final List<String> FALLBACK_SENSITIVE_EXTENSIONS = List.of(
            "pem", "key", "p12", "pfx", "jks", "keystore", "cert"
        );

        /// Thread-safe lazy initialization of resource-based defaults.
        /// Defers I/O until first access (GraalVM Native Image safe).
        static final class DefaultsHolder {
            static final List<String> IGNORED_DIRS = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK_IGNORED_DIRECTORIES);
            static final List<String> SOURCE_EXTS = ConfigDefaults.loadListFromResource(
                "defaults/source-extensions.txt", FALLBACK_SOURCE_EXTENSIONS);
            static final List<String> SENSITIVE_PATTERNS = ConfigDefaults.loadListFromResource(
                "defaults/sensitive-file-patterns.txt", FALLBACK_SENSITIVE_FILE_PATTERNS);
            static final List<String> SENSITIVE_EXTS = ConfigDefaults.loadListFromResource(
                "defaults/sensitive-extensions.txt", FALLBACK_SENSITIVE_EXTENSIONS);
        }

        public static final List<String> DEFAULT_IGNORED_DIRECTORIES = DefaultsHolder.IGNORED_DIRS;
        public static final List<String> DEFAULT_SOURCE_EXTENSIONS = DefaultsHolder.SOURCE_EXTS;
        public static final List<String> DEFAULT_SENSITIVE_FILE_PATTERNS = DefaultsHolder.SENSITIVE_PATTERNS;
        public static final List<String> DEFAULT_SENSITIVE_EXTENSIONS = DefaultsHolder.SENSITIVE_EXTS;

        public LocalFiles {
            maxFileSize = ConfigDefaults.defaultIfNonPositive(maxFileSize, DEFAULT_MAX_FILE_SIZE);
            maxTotalSize = ConfigDefaults.defaultIfNonPositive(maxTotalSize, DEFAULT_MAX_TOTAL_SIZE);
            ignoredDirectories = ConfigDefaults.defaultListIfEmpty(ignoredDirectories, DefaultsHolder.IGNORED_DIRS);
            sourceExtensions = ConfigDefaults.defaultListIfEmpty(sourceExtensions, DefaultsHolder.SOURCE_EXTS);
            sensitiveFilePatterns = ConfigDefaults.defaultListIfEmpty(sensitiveFilePatterns, DefaultsHolder.SENSITIVE_PATTERNS);
            sensitiveExtensions = ConfigDefaults.defaultListIfEmpty(sensitiveExtensions, DefaultsHolder.SENSITIVE_EXTS);
        }

        public LocalFiles() {
            this(DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_TOTAL_SIZE, null, null, null, null);
        }
    }

    // --- Skill configuration ---

    /// Configuration for skill file loading and execution tuning.
    /// Bound to `reviewer.skills` in YAML.
    @ConfigurationProperties("skills")
    public record Skills(
        @Nullable String filename,
        @Nullable String directory,
        int maxParameterValueLength,
        int maxExecutorCacheSize,
        int executorCacheInitialCapacity,
        int serviceShutdownTimeoutSeconds,
        int executorShutdownTimeoutSeconds
    ) {

        private static final String DEFAULT_FILENAME = "SKILL.md";
        private static final String DEFAULT_DIRECTORY = ".github/skills";
        public static final int DEFAULT_MAX_PARAMETER_VALUE_LENGTH = 10_000;
        public static final int DEFAULT_MAX_EXECUTOR_CACHE_SIZE = 16;
        public static final int DEFAULT_EXECUTOR_CACHE_INITIAL_CAPACITY = 16;
        public static final int DEFAULT_SERVICE_SHUTDOWN_TIMEOUT_SECONDS = 60;
        public static final int DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 30;

        public Skills {
            filename = ConfigDefaults.defaultIfBlank(filename, DEFAULT_FILENAME);
            directory = ConfigDefaults.defaultIfBlank(directory, DEFAULT_DIRECTORY);
            maxParameterValueLength = ConfigDefaults.defaultIfNonPositive(
                maxParameterValueLength, DEFAULT_MAX_PARAMETER_VALUE_LENGTH);
            maxExecutorCacheSize = ConfigDefaults.defaultIfNonPositive(
                maxExecutorCacheSize, DEFAULT_MAX_EXECUTOR_CACHE_SIZE);
            executorCacheInitialCapacity = ConfigDefaults.defaultIfNonPositive(
                executorCacheInitialCapacity, DEFAULT_EXECUTOR_CACHE_INITIAL_CAPACITY);
            serviceShutdownTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(
                serviceShutdownTimeoutSeconds, DEFAULT_SERVICE_SHUTDOWN_TIMEOUT_SECONDS);
            executorShutdownTimeoutSeconds = ConfigDefaults.defaultIfNonPositive(
                executorShutdownTimeoutSeconds, DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        }

        /// Creates a Skills config with all default values.
        public static Skills defaults() {
            return new Skills(null, null, 0, 0, 0, 0, 0);
        }
    }
}
