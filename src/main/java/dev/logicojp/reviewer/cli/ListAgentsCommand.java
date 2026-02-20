package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.CopilotCliException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Command to list all available review agents.
@Singleton
public class ListAgentsCommand {

    private static final Logger logger = LoggerFactory.getLogger(ListAgentsCommand.class);

    private static final String USAGE = """
            Usage: review list [options]

            Options:
                --agents-dir <path...>      Additional agent definition directories
            """;

    private final AgentService agentService;
    private final CliOutput output;

    record ParsedOptions(List<Path> additionalAgentDirs) {}

    @Inject
    public ListAgentsCommand(AgentService agentService, CliOutput output) {
        this.agentService = agentService;
        this.output = output;
    }

    public int execute(String[] args) {
        try {
            Optional<ParsedOptions> parsed = parseArgs(args);
            if (parsed.isEmpty()) return ExitCodes.OK;
            return executeInternal(parsed.get());
        } catch (CliValidationException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) output.errorln(e.getMessage());
            if (e.showUsage()) output.out().print(USAGE);
            return ExitCodes.USAGE;
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            output.errorln("Error: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
    }

    private Optional<ParsedOptions> parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);
        List<Path> additionalAgentDirs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    output.out().print(USAGE);
                    return Optional.empty();
                }
                case "--agents-dir" -> {
                    var values = CliParsing.readMultiValues(arg, args, i, "--agents-dir");
                    i = values.newIndex();
                    for (String path : values.values()) additionalAgentDirs.add(Path.of(path));
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliValidationException("Unknown option: " + arg, true);
                    }
                    throw new CliValidationException("Unexpected argument: " + arg, true);
                }
            }
        }

        return Optional.of(new ParsedOptions(List.copyOf(additionalAgentDirs)));
    }

    private int executeInternal(ParsedOptions options) {
        List<Path> agentDirs;
        List<String> availableAgents;
        try {
            agentDirs = agentService.buildAgentDirectories(options.additionalAgentDirs());
            availableAgents = agentService.listAvailableAgents(agentDirs);
        } catch (IOException e) {
            throw new CopilotCliException("Failed to list agents", e);
        }

        output.println("Agent directories:");
        for (Path dir : agentDirs) {
            output.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        output.println("");

        if (availableAgents.isEmpty()) {
            output.println("No agents found.");
            return ExitCodes.OK;
        }

        output.println("Available agents:");
        for (String agent : availableAgents) {
            output.println("  - " + agent);
        }
        return ExitCodes.OK;
    }
}
