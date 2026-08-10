package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ConfigureLoggingPort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Presentation entry component for global CLI parsing, dispatch, and output.
@Singleton
public final class CliApplication {

    private final Map<String, CliCommand> commandMap;
    private final CliOutput output;
    private final ConfigureLoggingPort logging;

    @Inject
    public CliApplication(List<CliCommand> commands,
                          CliOutput output,
                          ConfigureLoggingPort logging) {
        this.commandMap = buildCommandMap(commands);
        this.output = output;
        this.logging = logging;
    }

    private static Map<String, CliCommand> buildCommandMap(List<CliCommand> commands) {
        Map<String, CliCommand> map = new LinkedHashMap<>();
        for (CliCommand command : commands) {
            CliCommand previous = map.put(command.name(), command);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate CLI command name: " + command.name()
                        + " (" + previous.getClass().getSimpleName()
                        + " vs " + command.getClass().getSimpleName() + ")");
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /// Parses global options and dispatches to the selected command.
    public int execute(String[] args) {
        if (args == null || args.length == 0) {
            CliUsage.printGeneral(output);
            return ExitCodes.USAGE;
        }

        GlobalOptions globalOptions = parseGlobalOptions(args);
        if (globalOptions.verbose() && !logging.enableVerboseLogging()) {
            output.errorln("Failed to enable verbose logging (Logback not available)");
        }

        if (globalOptions.versionRequested()) {
            String version = getClass().getPackage().getImplementationVersion();
            output.println("Multi-Agent Reviewer " + (version != null ? version : "dev"));
            return ExitCodes.OK;
        }

        String[] filteredArgs = globalOptions.remainingArgs().toArray(String[]::new);
        if (filteredArgs.length == 0) {
            CliUsage.printGeneral(output);
            return ExitCodes.USAGE;
        }

        // Treat --help / -h as general help only when no subcommand is provided.
        boolean hasHelpFlag = CliParsing.hasHelpFlag(filteredArgs);
        boolean hasSubcommand = Arrays.stream(filteredArgs).anyMatch(commandMap::containsKey);
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
        CliCommand selected = commandMap.get(command);
        if (selected != null) {
            return selected.execute(commandArgs);
        }
        output.errorln("Unknown command: " + command);
        CliUsage.printGeneralError(output);
        return ExitCodes.USAGE;
    }

    private static GlobalOptions parseGlobalOptions(String[] args) {
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
}
