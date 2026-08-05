package dev.logicojp.reviewer.infrastructure.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/// Configuration for local file collection limits.
@ConfigurationProperties("reviewer.local-files")
public record LocalFileConfig(
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
        ".git", ".svn", ".hg", "node_modules", "bower_components",
        "target", "build", "out", "dist", "bin", "obj",
        ".gradle", ".mvn", ".idea", ".vscode", ".vs",
        "__pycache__", ".mypy_cache", ".pytest_cache",
        "vendor", ".bundle", "coverage", ".nyc_output", ".terraform"
    );
    private static final List<String> FALLBACK_SOURCE_EXTENSIONS = List.of(
        "java", "kt", "kts", "groovy", "scala", "clj",
        "js", "jsx", "ts", "tsx", "mjs", "cjs", "vue", "svelte",
        "c", "cpp", "cc", "cxx", "h", "hpp", "rs", "go", "zig",
        "py", "rb", "php", "pl", "pm", "lua", "r",
        "sh", "bash", "zsh", "fish", "ps1", "psm1",
        "cs", "fs", "vb", "swift", "m", "mm",
        "sql", "graphql", "gql", "proto",
        "yaml", "yml", "json", "toml", "xml", "properties",
        "gradle", "cmake", "makefile", "md", "rst", "adoc"
    );
    private static final List<String> FALLBACK_SENSITIVE_FILE_PATTERNS = List.of(
        "application-prod",
        "application-staging",
        "application-secret",
        "application-local",
        "application-dev",
        "application-ci",
        "secrets",
        "credentials",
        ".env",
        ".env.local",
        ".env.production",
        ".env.development",
        ".env.staging",
        ".env.test",
        "service-account",
        "keystore",
        "truststore",
        "id_rsa",
        "id_ed25519",
        "id_ecdsa",
        ".netrc",
        ".npmrc",
        ".pypirc",
        ".docker/config",
        "vault-config",
        "aws-credentials",
        "terraform.tfvars",
        "kubeconfig",
        ".kube/config",
        "htpasswd",
        "shadow",
        "application.yml",
        "application.yaml",
        "config.json",
        "settings.json",
        "settings.yaml"
    );
    private static final List<String> FALLBACK_SENSITIVE_EXTENSIONS = List.of(
        "pem", "key", "p12", "pfx", "jks", "keystore", "cert"
    );

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

    public LocalFileConfig {
        maxFileSize = ConfigDefaults.defaultIfNonPositive(maxFileSize, DEFAULT_MAX_FILE_SIZE);
        maxTotalSize = ConfigDefaults.defaultIfNonPositive(maxTotalSize, DEFAULT_MAX_TOTAL_SIZE);
        ignoredDirectories = ConfigDefaults.defaultListIfEmpty(ignoredDirectories, DefaultsHolder.IGNORED_DIRS);
        sourceExtensions = ConfigDefaults.defaultListIfEmpty(sourceExtensions, DefaultsHolder.SOURCE_EXTS);
        sensitiveFilePatterns = ConfigDefaults.defaultListIfEmpty(sensitiveFilePatterns, DefaultsHolder.SENSITIVE_PATTERNS);
        sensitiveExtensions = ConfigDefaults.defaultListIfEmpty(sensitiveExtensions, DefaultsHolder.SENSITIVE_EXTS);
    }

    public LocalFileConfig(long maxFileSize, long maxTotalSize) {
        this(maxFileSize, maxTotalSize, null, null, null, null);
    }

    public LocalFileConfig() {
        this(DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_TOTAL_SIZE, null, null, null, null);
    }
}
