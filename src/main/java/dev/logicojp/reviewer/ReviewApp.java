package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliCommand;
import dev.logicojp.reviewer.cli.CliParsing;
import dev.logicojp.reviewer.cli.CliOutput;
import dev.logicojp.reviewer.cli.CliUsage;
import dev.logicojp.reviewer.cli.ExitCodes;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Multi-Agent Code Reviewer CLI Application.
@Singleton
public class ReviewApp {
    private static final Logger logger = LoggerFactory.getLogger(ReviewApp.class);

    private final Map<String, CliCommand> commandMap;
    private final CliOutput output;

    @Inject
    public ReviewApp(List<CliCommand> commands, CliOutput output) {
        this.commandMap = buildCommandMap(commands);
        this.output = output;
    }

    private static Map<String, CliCommand> buildCommandMap(List<CliCommand> commands) {
        Map<String, CliCommand> map = new LinkedHashMap<>();
        for (CliCommand cmd : commands) {
            CliCommand previous = map.put(cmd.name(), cmd);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate CLI command name: " + cmd.name()
                        + " (" + previous.getClass().getSimpleName()
                        + " vs " + cmd.getClass().getSimpleName() + ")");
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static void main(String[] args) {
        ensureSecureLogDirectory();
        warnOnInsecureJvmFlags();
        int exitCode = ExitCodes.SOFTWARE;
        try (var context = ApplicationContext.run()) {
            var app = context.getBean(ReviewApp.class);
            exitCode = app.execute(args);
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void ensureSecureLogDirectory() {
        Path logDir = Path.of("logs");
        try {
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            if (Files.getFileAttributeView(logDir, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
                Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rwx------");
                Files.setPosixFilePermissions(logDir, ownerOnly);
            }
        } catch (UnsupportedOperationException _) {
            // Non-POSIX file system: best-effort only.
        } catch (IOException _) {
            // Logging may continue with environment defaults when hardening fails.
        }
    }

    private static void warnOnInsecureJvmFlags() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> insecureFlags = detectInsecureJvmFlags(runtimeMxBean.getInputArguments());
        if (insecureFlags.isEmpty()) {
            return;
        }
        logger.warn(
            "Potentially insecure JVM flags detected: {}. Heap dumps or OOM handlers may expose authentication tokens.",
            String.join(", ", insecureFlags)
        );
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

    public int execute(String[] args) {
        if (args == null || args.length == 0) {
            CliUsage.printGeneral(output);
            return ExitCodes.USAGE;
        }

        GlobalOptions globalOptions = parseGlobalOptions(args);
        boolean verbose = globalOptions.verbose();
        boolean versionRequested = globalOptions.versionRequested();
        List<String> remaining = globalOptions.remainingArgs();

        if (verbose) {
            enableVerboseLogging();
        }

        if (versionRequested) {
            String version = getClass().getPackage().getImplementationVersion();
            output.println("Multi-Agent Reviewer " + (version != null ? version : "dev"));
            return ExitCodes.OK;
        }

        String[] filteredArgs = remaining.toArray(String[]::new);
        if (filteredArgs.length == 0) {
            CliUsage.printGeneral(output);
            return ExitCodes.USAGE;
        }

        // Treat --help / -h as general help only when no subcommand is provided.
        boolean hasHelpFlag = CliParsing.hasHelpFlag(filteredArgs);
        boolean hasSubcommand = Arrays.stream(filteredArgs)
            .anyMatch(commandMap::containsKey);
        if (hasHelpFlag && !hasSubcommand) {
            CliUsage.printGeneral(output);
            return ExitCodes.OK;
        }

        int startIndex = 0;
        if ("review".equals(filteredArgs[0])) {
            if (filteredArgs.length == 1) {
                CliUsage.printGeneral(output);
                return ExitCodes.USAGE;
            }
            startIndex = 1;
        }

        String command = filteredArgs[startIndex];
        String[] commandArgs = Arrays.copyOfRange(filteredArgs, startIndex + 1, filteredArgs.length);

        return executeCommand(command, commandArgs);
    }

    private int executeCommand(String command, String[] commandArgs) {
        CliCommand cmd = commandMap.get(command);
        if (cmd != null) {
            return cmd.execute(commandArgs);
        }
        output.errorln("Unknown command: " + command);
        CliUsage.printGeneralError(output);
        return ExitCodes.USAGE;
    }

    private GlobalOptions parseGlobalOptions(String[] args) {
        boolean verbose = false;
        boolean versionRequested = false;
        List<String> remaining = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "-v", "--verbose" -> verbose = true;
                case "-V", "--version" -> versionRequested = true;
                default -> remaining.add(arg);
            }
        }
        return new GlobalOptions(verbose, versionRequested, List.copyOf(remaining));
    }

    private record GlobalOptions(boolean verbose, boolean versionRequested, List<String> remainingArgs) {
    }

    /// Enables debug-level logging at runtime.
    /// Delegates to {@link LogbackLevelSwitcher} to keep Logback-specific code isolated.
    private void enableVerboseLogging() {
        if (!LogbackLevelSwitcher.setDebug()) {
            output.errorln("Failed to enable verbose logging (Logback not available)");
        }
    }
}
