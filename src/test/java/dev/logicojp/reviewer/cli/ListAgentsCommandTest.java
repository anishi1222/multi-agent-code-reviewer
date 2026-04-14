package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.AgentPathConfig;
import dev.logicojp.reviewer.config.SkillConfig;
import dev.logicojp.reviewer.service.AgentService;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ListAgentsCommand")
class ListAgentsCommandTest {

    @Test
    @DisplayName("利用可能なエージェント一覧を表示して終了コード0を返す")
    void printsAvailableAgents() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));

        AgentService service = new AgentService(
            SkillConfig.defaults(),
            new TemplateService(new dev.logicojp.reviewer.config.TemplateConfig("templates", null, null, null, null, null, null, null, null)),
            new AgentPathConfig(List.of("."))
        ) {
            @Override
            public List<Path> buildAgentDirectories(List<Path> additionalDirs) {
                return List.of(Path.of("."));
            }

            @Override
            public List<String> listAvailableAgents(List<Path> agentDirs) {
                return List.of("security", "performance");
            }
        };

        ListAgentsCommand command = new ListAgentsCommand(service, output);
        int exit = command.execute(new String[0]);

        String outText = out.toString();
        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(outText).contains("Available agents:");
        assertThat(outText).contains("security");
        assertThat(outText).contains("performance");
    }

    @Test
    @DisplayName("不正オプション時はUSAGEを返す")
    void returnsUsageOnUnknownOption() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));

        AgentService service = new AgentService(
            SkillConfig.defaults(),
            new TemplateService(new dev.logicojp.reviewer.config.TemplateConfig("templates", null, null, null, null, null, null, null, null)),
            new AgentPathConfig(List.of("."))
        );

        ListAgentsCommand command = new ListAgentsCommand(service, output);
        int exit = command.execute(new String[]{"--unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
        assertThat(err.toString()).contains("Unknown option");
    }
}
