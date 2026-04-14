package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.service.SkillService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// Skill command that executes individual agent skills.
@Singleton
public class SkillCommand implements CliCommand {
    private static final Logger logger = LoggerFactory.getLogger(SkillCommand.class);

    private final SkillService skillService;
    private final ExecutionConfig executionConfig;
    private final SkillExecutionPreparation executionPreparation;
    private final SkillExecutionCoordinator executionCoordinator;
    private final SkillOptionsParser optionsParser;
    private final SkillOutputFormatter outputFormatter;
    private final CliOutput output;

    /// Parsed CLI options for the skill command.
    record ParsedOptions(
        String skillId,
        List<String> paramStrings,
        String githubToken,
        String model,
        List<Path> additionalAgentDirs,
        boolean listSkills
    ) {
        ParsedOptions {
            paramStrings = paramStrings != null ? List.copyOf(paramStrings) : List.of();
            additionalAgentDirs = additionalAgentDirs != null ? List.copyOf(additionalAgentDirs) : List.of();
        }
    }

    @Inject
    public SkillCommand(
        SkillService skillService,
        ExecutionConfig executionConfig,
        SkillExecutionPreparation executionPreparation,
        SkillExecutionCoordinator executionCoordinator,
        SkillOptionsParser optionsParser,
        SkillOutputFormatter outputFormatter,
        CliOutput output
    ) {
        this.skillService = skillService;
        this.executionConfig = executionConfig;
        this.executionPreparation = executionPreparation;
        this.executionCoordinator = executionCoordinator;
        this.optionsParser = optionsParser;
        this.outputFormatter = outputFormatter;
        this.output = output;
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

    private Optional<ParsedOptions> parseArgs(String[] args) {
        return optionsParser.parse(args);
    }

    private int executeInternal(ParsedOptions options) {
        SkillExecutionPreparation.PreparationResult prepared = executionPreparation.prepare(options);
        if (isListOnly(prepared)) {
            return printAvailableSkills();
        }

        return executeSkill(options, prepared);
    }

    private boolean isListOnly(SkillExecutionPreparation.PreparationResult prepared) {
        return prepared.listOnly();
    }

    private int printAvailableSkills() {
        outputFormatter.printAvailableSkills(skillService.getRegistry().getAll());
        return ExitCodes.OK;
    }

    private int executeSkill(ParsedOptions options,
                             SkillExecutionPreparation.PreparationResult prepared) {
        return executionCoordinator.execute(
            options.skillId(),
            prepared.parameters(),
            prepared.resolvedToken(),
            options.model(),
            executionConfig.skillTimeoutMinutes()
        );
    }

}
