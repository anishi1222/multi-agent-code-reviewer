package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.AgentPathConfig;
import dev.logicojp.reviewer.config.SkillConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentService")
class AgentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("既存の設定ディレクトリと追加ディレクトリを結合して返す")
    void buildAgentDirectoriesCombinesConfiguredAndAdditional() throws IOException {
        Path configured = tempDir.resolve("agents");
        Files.createDirectories(configured);
        Path extra = tempDir.resolve("extra");
        Files.createDirectories(extra);

        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            "default-output-format.md", null, null, null, null, null, null, null));
        Files.writeString(tempDir.resolve("default-output-format.md"), "## Output");

        AgentService service = new AgentService(
            SkillConfig.defaults(),
            templateService,
            new AgentPathConfig(List.of(configured.toString(), tempDir.resolve("missing").toString()))
        );

        List<Path> directories = service.buildAgentDirectories(List.of(extra));

        assertThat(directories).containsExactly(configured, extra);
    }

    @Test
    @DisplayName("ロード済みエージェントを一覧できる")
    void listAvailableAgentsReturnsAgentNames() throws IOException {
        Path configured = tempDir.resolve("agents");
        Files.createDirectories(configured);
        Files.writeString(configured.resolve("security.agent.md"), """
            ---
            name: security
            description: security agent
            model: gpt-4o
            ---

            ## Role
            role

            ## Instruction
            ${repository}

            ## Output Format
            ## Output

            ## Focus Areas
            - a
            """);
        Files.writeString(tempDir.resolve("default-output-format.md"), "## Output");

        TemplateService templateService = new TemplateService(new TemplateConfig(tempDir.toString(),
            "default-output-format.md", null, null, null, null, null, null, null));
        AgentService service = new AgentService(
            SkillConfig.defaults(),
            templateService,
            new AgentPathConfig(List.of(configured.toString()))
        );

        Map<String, AgentConfig> all = service.loadAllAgents(List.of(configured));
        List<String> names = service.listAvailableAgents(List.of(configured));

        assertThat(all).containsKey("security");
        assertThat(names).contains("security");
    }
}
