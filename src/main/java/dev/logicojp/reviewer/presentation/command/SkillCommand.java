package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.presentation.CliCommand;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.CliUsage;
import dev.logicojp.reviewer.presentation.ExitCodes;
import dev.logicojp.reviewer.presentation.SkillExecutionCoordinator;
import dev.logicojp.reviewer.presentation.SkillExecutionPreparation;
import dev.logicojp.reviewer.presentation.SkillOptions;
import dev.logicojp.reviewer.presentation.formatter.SkillOutputFormatter;
import dev.logicojp.reviewer.presentation.parser.SkillOptionsParser;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/// Skill command that executes individual agent skills via inbound ports.
@Singleton
public class SkillCommand implements CliCommand {

    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);

    private final ExecuteSkillPort executeSkillPort;
    private final SkillExecutionPreparation executionPreparation;
    private final SkillExecutionCoordinator executionCoordinator;
    private final SkillOptionsParser optionsParser;
    private final SkillOutputFormatter outputFormatter;
    private final CliOutput output;
    private final long skillTimeoutMinutes;

    @Inject
    public SkillCommand(
            ExecuteSkillPort executeSkillPort,
            SkillExecutionPreparation executionPreparation,
            SkillExecutionCoordinator executionCoordinator,
            SkillOptionsParser optionsParser,
            SkillOutputFormatter outputFormatter,
            CliOutput output,
            @Value("${reviewer.execution.skill-timeout-minutes:10}") long skillTimeoutMinutes) {
        this.executeSkillPort = executeSkillPort;
        this.executionPreparation = executionPreparation;
        this.executionCoordinator = executionCoordinator;
        this.optionsParser = optionsParser;
        this.outputFormatter = outputFormatter;
        this.output = output;
        this.skillTimeoutMinutes = skillTimeoutMinutes;
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public int execute(String[] args) {
        return CommandExecutor.execute(
            args,
            this::parseArgs,
            this::executeInternal,
            CliUsage::printSkill,
            logger,
            output
        );
    }

    private Optional<SkillOptions> parseArgs(String[] args) {
        return optionsParser.parse(args);
    }

    private int executeInternal(SkillOptions options) {
        SkillExecutionPreparation.PreparationResult prepared = executionPreparation.prepare(options);
        if (prepared.listOnly()) {
            return printAvailableSkills();
        }
        return executionCoordinator.execute(
            options.skillId(),
            prepared.parameters(),
            prepared.resolvedToken(),
            options.model(),
            skillTimeoutMinutes
        );
    }

    private int printAvailableSkills() {
        outputFormatter.printAvailableSkills(executeSkillPort.listSkills());
        return ExitCodes.OK;
    }
}
