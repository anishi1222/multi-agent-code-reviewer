package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckDialogueExecutor")
class RubberDuckDialogueExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("last-responder synthesis uses Session B and includes dialogue history")
    void executesLastResponderDialogue() throws IOException {
        writeJapaneseTemplates();
        AgentConfig config = agentConfig("model-a", null, 1, "ja");
        RecordingSessionFactory sessionFactory = new RecordingSessionFactory(Map.of(
            "A", List.of("A initial review"),
            "B", List.of("B peer review", "FINAL REVIEW")
        ));

        ReviewResult result = executor(config, rubberDuck("model-b", "last-responder"), sessionFactory)
            .execute(ReviewTarget.gitHub("owner/repo"), "REVIEW INSTRUCTION", "LOCAL SOURCE", null);

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("FINAL REVIEW");
        assertThat(sessionFactory.calls())
            .extracting(CreateCall::tag)
            .containsExactly("A", "B");
        assertThat(sessionFactory.session("A").prompts().getFirst())
            .contains("初回テンプレート")
            .contains("REVIEW INSTRUCTION")
            .contains("LOCAL SOURCE");
        assertThat(sessionFactory.session("B").prompts().get(0))
            .contains("ピア:A initial review");
        assertThat(sessionFactory.session("B").prompts().get(1))
            .contains("統合テンプレート")
            .contains("## Dialogue History")
            .contains("A initial review")
            .contains("B peer review")
            .contains("## Output Format");
        assertThat(sessionFactory.session("A").closed()).isTrue();
        assertThat(sessionFactory.session("B").closed()).isTrue();
    }

    @Test
    @DisplayName("multiple rounds alternate counter prompts")
    void executesMultipleRounds() throws IOException {
        writeJapaneseTemplates();
        AgentConfig config = agentConfig("model-a", null, 2, "ja");
        RecordingSessionFactory sessionFactory = new RecordingSessionFactory(Map.of(
            "A", List.of("A initial", "A counter"),
            "B", List.of("B peer", "B counter", "FINAL")
        ));

        ReviewResult result = executor(config, rubberDuck("model-b", "last-responder"), sessionFactory)
            .execute(ReviewTarget.gitHub("owner/repo"), "INSTRUCTION", null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("FINAL");
        assertThat(sessionFactory.session("A").prompts()).hasSize(2);
        assertThat(sessionFactory.session("B").prompts()).hasSize(3);
        assertThat(sessionFactory.session("A").prompts().get(1)).contains("反論:B peer");
        assertThat(sessionFactory.session("B").prompts().get(1)).contains("反論:A counter");
        assertThat(sessionFactory.session("B").prompts().get(2))
            .contains("### Round 1")
            .contains("### Round 2")
            .contains("A counter")
            .contains("B counter");
    }

    @Test
    @DisplayName("dedicated-session synthesis creates a separate synthesis session")
    void executesDedicatedSynthesis() throws IOException {
        writeJapaneseTemplates();
        AgentConfig config = agentConfig("model-a", null, 1, "ja");
        RecordingSessionFactory sessionFactory = new RecordingSessionFactory(Map.of(
            "A", List.of("A initial"),
            "B", List.of("B peer"),
            "synthesis", List.of("DEDICATED FINAL")
        ));

        ReviewResult result = executor(config, rubberDuck("model-b", "dedicated-session"), sessionFactory)
            .execute(ReviewTarget.gitHub("owner/repo"), "INSTRUCTION", null, Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("DEDICATED FINAL");
        assertThat(sessionFactory.calls())
            .extracting(CreateCall::tag)
            .containsExactly("A", "B", "synthesis");
        assertThat(sessionFactory.call("synthesis").model()).isEqualTo("model-a");
        assertThat(sessionFactory.call("synthesis").mcpServers()).isNull();
        assertThat(sessionFactory.session("synthesis").prompts().getFirst())
            .contains("統合テンプレート")
            .contains("A initial")
            .contains("B peer");
        assertThat(sessionFactory.session("synthesis").closed()).isTrue();
    }

    @Test
    @DisplayName("same model rejection is returned as failed ReviewResult")
    void returnsFailureWhenPeerModelMatchesPrimaryModel() throws IOException {
        writeJapaneseTemplates();
        AgentConfig config = agentConfig("same-model", null, 1, "ja");
        RecordingSessionFactory sessionFactory = new RecordingSessionFactory(Map.of());

        ReviewResult result = executor(config, rubberDuck("same-model", "last-responder"), sessionFactory)
            .execute(ReviewTarget.gitHub("owner/repo"), "INSTRUCTION", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("requires different models");
        assertThat(sessionFactory.calls()).isEmpty();
    }

    @Test
    @DisplayName("language-specific templates are used before Japanese fallback")
    void usesLanguageTemplateBeforeFallback() throws IOException {
        writeJapaneseTemplates();
        writeTemplate("rubber-duck-initial-en.md", "EN initial");
        AgentConfig config = agentConfig("model-a", null, 1, "en");
        RecordingSessionFactory sessionFactory = new RecordingSessionFactory(Map.of(
            "A", List.of("A en"),
            "B", List.of("B ja fallback", "FINAL")
        ));

        ReviewResult result = executor(config, rubberDuck("model-b", "last-responder"), sessionFactory)
            .execute(ReviewTarget.gitHub("owner/repo"), "INSTRUCTION", null, null);

        assertThat(result.success()).isTrue();
        assertThat(sessionFactory.session("A").prompts().getFirst()).contains("EN initial");
        assertThat(sessionFactory.session("B").prompts().getFirst()).contains("ピア:A en");
    }

    @Test
    @DisplayName("session config factory sanitizes session ids and applies MCP servers")
    void sdkSessionFactoryBuildsSessionConfig() {
        AgentConfig config = agentConfig("agent/name", "model-a", null, 1, "ja");
        ReviewContext ctx = ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026/06/24 13:00")
            .maxRetries(0)
            .reasoningEffort("high")
            .localFileConfig(new dev.logicojp.reviewer.config.LocalFileConfig())
            .build();
        var factory = new SdkRubberDuckSessionFactory(config, ctx);
        var mcpServers = Map.<String, com.github.copilot.rpc.McpServerConfig>of(
            "github",
            new com.github.copilot.rpc.McpHttpServerConfig().setUrl("https://example.com")
        );

        var sessionConfig = factory.buildSessionConfig("model-a", "system", mcpServers, "tag/1");

        assertThat(sessionConfig.getModel()).isEqualTo("model-a");
        assertThat(sessionConfig.getSessionId()).isEqualTo("agent-name_rubber-duck_tag-1_2026-06-24-13-00");
        assertThat(sessionConfig.getSystemMessage().getContent()).isEqualTo("system");
        assertThat(sessionConfig.getMcpServers()).containsKey("github");
    }

    private RubberDuckDialogueExecutor executor(AgentConfig config,
                                                RubberDuckConfig rubberDuckConfig,
                                                RecordingSessionFactory sessionFactory) {
        var promptBuilder = new RubberDuckPromptBuilder(config, context(), templateService());
        return new RubberDuckDialogueExecutor(
            config,
            rubberDuckConfig,
            promptBuilder,
            sessionFactory,
            new ReviewResultFactory()
        );
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .client(new com.github.copilot.CopilotClient(new com.github.copilot.rpc.CopilotClientOptions()))
            .timeoutMinutes(1)
            .idleTimeoutMinutes(1)
            .invocationTimestamp("2026-06-24-13-00-00")
            .maxRetries(0)
            .outputConstraints("OUTPUT CONSTRAINTS")
            .localFileConfig(new dev.logicojp.reviewer.config.LocalFileConfig())
            .build();
    }

    private TemplateService templateService() {
        return new TemplateService(new TemplateConfig(tempDir.toString(), null, null, null, null, null, null, null, null));
    }

    private AgentConfig agentConfig(String model, String peerModel, int dialogueRounds, String language) {
        return agentConfig("agent", model, peerModel, dialogueRounds, language);
    }

    private AgentConfig agentConfig(String name, String model, String peerModel, int dialogueRounds, String language) {
        return AgentConfig.builder()
            .name(name)
            .displayName("Agent")
            .model(model)
            .systemPrompt("SYSTEM")
            .instruction("BASE INSTRUCTION")
            .outputFormat("## Output Format\n\n- finding")
            .focusAreas(List.of("quality"))
            .skills(List.of())
            .peerModel(peerModel)
            .rubberDuckEnabled(true)
            .dialogueRounds(dialogueRounds)
            .language(language)
            .build();
    }

    private RubberDuckConfig rubberDuck(String peerModel, String synthesisStrategy) {
        return new RubberDuckConfig(true, 1, peerModel, synthesisStrategy);
    }

    private void writeJapaneseTemplates() throws IOException {
        writeTemplate("rubber-duck-initial-ja.md", "初回テンプレート");
        writeTemplate("rubber-duck-peer-review-ja.md", "ピア:${peerReviewContent}");
        writeTemplate("rubber-duck-counter-ja.md", "反論:${peerReviewContent}");
        writeTemplate("rubber-duck-synthesis-ja.md", "統合テンプレート");
    }

    private void writeTemplate(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }

    private record CreateCall(String tag,
                              String model,
                              String systemPrompt,
                              Map<String, com.github.copilot.rpc.McpServerConfig> mcpServers) {
    }

    private static final class RecordingSessionFactory implements RubberDuckSessionFactory {
        private final Map<String, ArrayDeque<String>> responsesByTag = new HashMap<>();
        private final Map<String, RecordingSession> sessions = new HashMap<>();
        private final List<CreateCall> calls = new ArrayList<>();

        private RecordingSessionFactory(Map<String, List<String>> responsesByTag) {
            responsesByTag.forEach((tag, responses) -> this.responsesByTag.put(tag, new ArrayDeque<>(responses)));
        }

        @Override
        public RubberDuckSession create(String model,
                                        String systemPrompt,
                                        Map<String, com.github.copilot.rpc.McpServerConfig> mcpServers,
                                        String sessionTag) {
            calls.add(new CreateCall(sessionTag, model, systemPrompt, mcpServers));
            var session = new RecordingSession(responsesByTag.getOrDefault(sessionTag, new ArrayDeque<>()));
            sessions.put(sessionTag, session);
            return session;
        }

        List<CreateCall> calls() {
            return calls;
        }

        CreateCall call(String tag) {
            return calls.stream()
                .filter(call -> call.tag().equals(tag))
                .findFirst()
                .orElseThrow();
        }

        RecordingSession session(String tag) {
            return sessions.get(tag);
        }
    }

    private static final class RecordingSession implements RubberDuckSession {
        private final ArrayDeque<String> responses;
        private final List<String> prompts = new ArrayList<>();
        private boolean closed;

        private RecordingSession(ArrayDeque<String> responses) {
            this.responses = responses;
        }

        @Override
        public String send(String prompt) {
            prompts.add(prompt);
            return responses.isEmpty() ? "" : responses.removeFirst();
        }

        @Override
        public void close() {
            closed = true;
        }

        List<String> prompts() {
            return prompts;
        }

        boolean closed() {
            return closed;
        }
    }
}
