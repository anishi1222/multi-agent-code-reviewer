package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckDialogueRunner")
class RubberDuckDialogueRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("rounds分のA/B対話を順番に実行する")
    void conductsConfiguredRounds() throws Exception {
        writeTemplate("rubber-duck-initial-ja.md", "INITIAL");
        writeTemplate("rubber-duck-peer-review-ja.md", "PEER:${peerReviewContent}");
        writeTemplate("rubber-duck-counter-ja.md", "COUNTER:${peerReviewContent}");
        AgentConfig config = agent();
        RubberDuckDialogueRunner runner = new RubberDuckDialogueRunner(config, promptBuilder(config));
        RecordingSession sessionA = new RecordingSession("A1", "A2");
        RecordingSession sessionB = new RecordingSession("B1", "B2");

        List<DialogueRound> rounds = runner.conduct(sessionA, sessionB, "INSTRUCTION", "SOURCE", "model-b", 2);

        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(0).contentA()).isEqualTo("A1");
        assertThat(rounds.get(0).contentB()).isEqualTo("B1");
        assertThat(rounds.get(1).contentA()).isEqualTo("A2");
        assertThat(rounds.get(1).contentB()).isEqualTo("B2");
        assertThat(sessionA.prompts()).hasSize(2);
        assertThat(sessionA.prompts().get(0)).contains("INITIAL").contains("INSTRUCTION").contains("SOURCE");
        assertThat(sessionA.prompts().get(1)).contains("COUNTER:B1");
        assertThat(sessionB.prompts()).containsExactly("PEER:A1", "COUNTER:A2");
    }

    private RubberDuckPromptBuilder promptBuilder(AgentConfig config) {
        return new RubberDuckPromptBuilder(config, context(), templateService());
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-06-24-14-00-00")
            .maxRetries(0)
            .localFileConfig(new LocalFileConfig())
            .build();
    }

    private TemplateService templateService() {
        return new TemplateService(new TemplateConfig(tempDir.toString(), null, null, null, null, null, null, null, null));
    }

    private AgentConfig agent() {
        return new AgentConfig("agent", "Agent", "model-a", "system", "instruction", null, List.of(), List.of());
    }

    private void writeTemplate(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }

    private static final class RecordingSession implements RubberDuckSession {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();

        RecordingSession(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public String send(String prompt) {
            prompts.add(prompt);
            return responses.removeFirst();
        }

        @Override
        public void close() {
        }

        List<String> prompts() {
            return prompts;
        }
    }
}
