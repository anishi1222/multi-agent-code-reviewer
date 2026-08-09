package dev.logicojp.reviewer.infrastructure.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/// Host adapter for log-directory hardening and insecure JVM-flag diagnostics.
public final class SystemStartupEnvironment implements StartupEnvironment {

    // Preserve the operational logger name published in docs/runbook.md while keeping this adapter
    // independent of the layer-zero ReviewApp type.
    private static final Logger logger =
        LoggerFactory.getLogger("dev.logicojp.reviewer.ReviewApp");

    private final Path logDirectory;
    private final Supplier<List<String>> jvmInputArguments;

    public SystemStartupEnvironment() {
        this(Path.of("logs"), () -> ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    SystemStartupEnvironment(Path logDirectory, Supplier<List<String>> jvmInputArguments) {
        this.logDirectory = logDirectory;
        this.jvmInputArguments = jvmInputArguments;
    }

    @Override
    public void prepare() {
        ensureSecureLogDirectory();
        warnOnInsecureJvmFlags();
    }

    private void ensureSecureLogDirectory() {
        try {
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
            }
            if (Files.getFileAttributeView(logDirectory, PosixFileAttributeView.class) != null) {
                Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rwx------");
                Files.setPosixFilePermissions(logDirectory, ownerOnly);
            }
        } catch (UnsupportedOperationException _) {
            // Non-POSIX file system: best-effort only.
        } catch (IOException _) {
            // Logging may continue with environment defaults when hardening fails.
        }
    }

    private void warnOnInsecureJvmFlags() {
        List<String> insecureFlags = detectInsecureJvmFlags(jvmInputArguments.get());
        if (!insecureFlags.isEmpty()) {
            logger.warn(
                "Potentially insecure JVM flags detected: {}. "
                    + "Heap dumps or OOM handlers may expose authentication tokens.",
                String.join(", ", insecureFlags)
            );
        }
    }

    static List<String> detectInsecureJvmFlags(List<String> inputArguments) {
        if (inputArguments == null || inputArguments.isEmpty()) {
            return List.of();
        }
        List<String> insecure = new ArrayList<>();
        if (isEnabledFlagPresent(inputArguments, "HeapDumpOnOutOfMemoryError")) {
            insecure.add("HeapDumpOnOutOfMemoryError");
        }
        if (isFlagPresent(inputArguments, "OnOutOfMemoryError")) {
            insecure.add("OnOutOfMemoryError");
        }
        return List.copyOf(insecure);
    }

    private static boolean isEnabledFlagPresent(List<String> inputArguments, String flagName) {
        return inputArguments.stream()
            .anyMatch(arg -> arg.contains(flagName) && !arg.startsWith("-XX:-"));
    }

    private static boolean isFlagPresent(List<String> inputArguments, String flagName) {
        return inputArguments.stream().anyMatch(arg -> arg.contains(flagName));
    }
}
