package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.ExitCodes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/// Coordinates skill execution via inbound port.
///
/// No lifecycle management — the port adapter handles Copilot session lifecycle internally.
@Singleton
public class SkillExecutionCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutionCoordinator.class);

    private final ExecuteSkillPort executeSkillPort;
    private final CliOutput output;

    @Inject
    public SkillExecutionCoordinator(ExecuteSkillPort executeSkillPort, CliOutput output) {
        this.executeSkillPort = executeSkillPort;
        this.output = output;
    }

    public int execute(String skillId,
                       Map<String, String> parameters,
                       String resolvedToken,
                       String model) {
        output.println("Executing skill: " + skillId);
        output.println("Parameters: " + parameters.keySet());

        SkillResult result = executeSkillPort.execute(skillId, parameters);
        if (result.success()) {
            output.println("=== Skill Result ===\n");
            output.println(result.content());
            return ExitCodes.OK;
        }
        output.errorln("Skill execution failed: " + result.errorMessage());
        return ExitCodes.SOFTWARE;
    }
}
