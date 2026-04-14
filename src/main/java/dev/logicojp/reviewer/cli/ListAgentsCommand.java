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
public class ListAgentsCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(ListAgentsCommand.class);

    private final AgentService agentService;
    private final CliOutput output;

    /// Parsed CLI options for the list command.
    record ParsedOptions(List<Path> additionalAgentDirs) {}

    @Inject
    public ListAgentsCommand(AgentService agentService, CliOutput output) {
        this.agentService = agentService;
        this.output = output;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printList,
            logger,
            output
        );
    }

    private Optional<ParsedOptions> parseArgs(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);
        List<Path> additionalAgentDirs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    CliUsage.printList(output);
                    return Optional.empty();
                }
                case "--agents-dir" -> {
                    CliParsing.MultiValue values = CliParsing.readMultiValues(arg, args, i, "--agents-dir");
                    i = values.newIndex();
                    for (String path : values.values()) {
                        additionalAgentDirs.add(Path.of(path));
                    }
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

        printAgentDirectories(agentDirs);

        if (availableAgents.isEmpty()) {
            return printNoAgents();
        }

        printAvailableAgents(availableAgents);
        return ExitCodes.OK;
    }

    private void printAgentDirectories(List<Path> agentDirs) {
        output.println("Agent directories:");
        for (Path dir : agentDirs) {
            output.println("  - " + dir + (Files.exists(dir) ? "" : " (not found)"));
        }
        output.println("");
    }

    private int printNoAgents() {
        output.println("No agents found.");
        return ExitCodes.OK;
    }

    private void printAvailableAgents(List<String> availableAgents) {
        output.println("Available agents:");
        for (String agent : availableAgents) {
            output.println("  - " + agent);
        }
    }
}
