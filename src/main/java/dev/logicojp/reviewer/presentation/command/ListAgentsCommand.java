package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.presentation.CliCommand;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.CliParsing;
import dev.logicojp.reviewer.presentation.CliUsage;
import dev.logicojp.reviewer.presentation.CliValidationException;
import dev.logicojp.reviewer.presentation.ExitCodes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Command to list all available review agents via {@link LoadAgentPort}.
@Singleton
public class ListAgentsCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(ListAgentsCommand.class);

    record ParsedOptions(List<Path> additionalAgentDirs) {}

    private final LoadAgentPort loadAgentPort;
    private final CliOutput output;

    @Inject
    public ListAgentsCommand(LoadAgentPort loadAgentPort, CliOutput output) {
        this.loadAgentPort = loadAgentPort;
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
        List<AgentConfig> agents = loadAgentPort.loadAll(options.additionalAgentDirs());

        if (agents.isEmpty()) {
            output.println("No agents found.");
            return ExitCodes.OK;
        }

        output.println("Available agents:");
        for (AgentConfig agent : agents) {
            output.println("  - " + agent.name());
        }
        return ExitCodes.OK;
    }
}
