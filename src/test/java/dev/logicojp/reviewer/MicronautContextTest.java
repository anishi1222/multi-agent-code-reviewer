package dev.logicojp.reviewer;

import dev.logicojp.reviewer.cli.CliCommand;
import dev.logicojp.reviewer.config.ExecutionConfig;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(environments = Environment.CLI)
@DisplayName("Micronaut ApplicationContext")
class MicronautContextTest {

    @Inject
    ApplicationContext context;

    @Inject
    ExecutionConfig executionConfig;

    @Inject
    List<CliCommand> commands;

    @Test
    @DisplayName("CLI環境でApplicationContextが起動する")
    void contextStartsWithCliEnvironment() {
        assertThat(context.isRunning()).isTrue();
        assertThat(context.getEnvironment().getActiveNames()).contains(Environment.CLI);
    }

    @Test
    @DisplayName("ConfigurationPropertiesがバインドされる")
    void configurationPropertiesAreBound() {
        assertThat(executionConfig.parallelism()).isEqualTo(4);
        assertThat(executionConfig.agentTimeoutMinutes()).isEqualTo(20);
    }

    @Test
    @DisplayName("CLIコマンドBeanが登録される")
    void cliCommandsAreRegistered() {
        assertThat(commands).extracting(CliCommand::name)
            .contains("run", "list", "doctor", "skill");
    }
}
