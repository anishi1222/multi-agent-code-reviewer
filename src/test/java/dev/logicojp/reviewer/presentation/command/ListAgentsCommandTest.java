package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.ExitCodes;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ListAgentsCommand")
class ListAgentsCommandTest {

    @Test
    @DisplayName("利用可能なエージェント一覧を表示して終了コード0を返す")
    void printsAvailableAgents() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));
        AtomicReference<List<AgentSourceDirectory>> requestedDirs = new AtomicReference<>();
        LoadAgentPort loadAgentPort = stubLoadAgentPort(requestedDirs, List.of(
            agentConfig("security"),
            agentConfig("performance")
        ));

        ListAgentsCommand command = new ListAgentsCommand(loadAgentPort, output);
        int exit = command.execute(new String[]{"--agents-dir", "."});

        String outText = out.toString();
        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(requestedDirs.get())
            .as("a directory named on the command line is operator input, not repository input")
            .containsExactly(AgentSourceDirectory.userSupplied(Path.of(".")));
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

        ListAgentsCommand command = new ListAgentsCommand(stubLoadAgentPort(new AtomicReference<>(), List.of()), output);
        int exit = command.execute(new String[]{"--unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
        assertThat(err.toString()).contains("Unknown option");
    }

    private static LoadAgentPort stubLoadAgentPort(AtomicReference<List<AgentSourceDirectory>> requestedDirs,
                                                   List<AgentConfig> agents) {
        return new LoadAgentPort() {
            @Override
            public List<AgentConfig> loadAll(List<AgentSourceDirectory> directories) {
                requestedDirs.set(directories);
                return agents;
            }

            @Override
            public Optional<AgentConfig> loadByName(String name, List<AgentSourceDirectory> directories) {
                return agents.stream().filter(a -> a.name().equals(name)).findFirst();
            }
        };
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(name, name, "gpt-5", "prompt", "instruction", "", List.of(), List.of());
    }
}
