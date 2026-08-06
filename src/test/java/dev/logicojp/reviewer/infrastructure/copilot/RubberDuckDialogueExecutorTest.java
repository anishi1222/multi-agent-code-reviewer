package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.DialogueRound;
import dev.logicojp.reviewer.domain.agent.ReviewSystemPromptFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckDialogueExecutor")
class RubberDuckDialogueExecutorTest {

    @Test
    @DisplayName("multiple rounds alternate counter prompts")
    void executesMultipleRounds() throws Exception {
        AgentConfig agentA = agentConfig("agent-a", "model-a");
        AgentConfig agentB = agentConfig("agent-b", "model-b");
        RecordingSession sessionA = new RecordingSession(List.of("A initial", "A counter"));
        RecordingSession sessionB = new RecordingSession(List.of("B peer", "B counter"));

        List<DialogueRound> rounds = conductDialogue(sessionA, sessionB, agentA, agentB, "INITIAL", 2);

        assertThat(rounds).containsExactly(
            new DialogueRound(1, "model-a", "A initial", "model-b", "B peer"),
            new DialogueRound(2, "model-a", "A counter", "model-b", "B counter")
        );
        assertThat(sessionA.prompts()).containsExactly(
            "INITIAL",
            "The other reviewer provided the following perspective. Please respond constructively:\n\nB peer"
        );
        assertThat(sessionB.prompts()).containsExactly(
            "The other reviewer provided the following perspective. Please respond constructively:\n\nA initial",
            "The other reviewer provided the following perspective. Please respond constructively:\n\nA counter"
        );
    }

    @Test
    @DisplayName("null responses are converted to empty dialogue content")
    void convertsNullResponsesToEmptyContent() throws Exception {
        AgentConfig agentA = agentConfig("agent-a", "model-a");
        AgentConfig agentB = agentConfig("agent-b", "model-b");
        RecordingSession sessionA = new RecordingSession(Collections.singletonList((String) null));
        RecordingSession sessionB = new RecordingSession(Collections.singletonList((String) null));

        List<DialogueRound> rounds = conductDialogue(sessionA, sessionB, agentA, agentB, "INITIAL", 1);

        assertThat(rounds).containsExactly(new DialogueRound(1, "model-a", "", "model-b", ""));
    }

    @Test
    @DisplayName("session config factory sanitizes session id tokens")
    void sdkSessionFactorySanitizesSessionIdTokens() {
        assertThat(SdkRubberDuckSessionFactory.sanitize("agent/name")).isEqualTo("agent-name");
        assertThat(SdkRubberDuckSessionFactory.sanitize("2026/06/24 13:00")).isEqualTo("2026-06-24-13-00");
        assertThat(SdkRubberDuckSessionFactory.sanitize(" ")).isEqualTo("unknown");
    }

    // removed: last-responder/dedicated-session synthesis moved to application.review.RubberDuckDialogueRunner.
    // removed: same-model rejection no longer belongs to RubberDuckDialogueExecutor; the executor now runs any two AgentConfig values supplied by its caller.
    // removed: language-specific template selection moved to application.review.RubberDuckDialogueRunner via LoadTemplatePort.

    @SuppressWarnings("unchecked")
    private static List<DialogueRound> conductDialogue(RecordingSession sessionA,
                                                       RecordingSession sessionB,
                                                       AgentConfig agentA,
                                                       AgentConfig agentB,
                                                       String initialPrompt,
                                                       int rounds) throws Exception {
        Method method = RubberDuckDialogueExecutor.class.getDeclaredMethod(
            "conductDialogue",
            SdkRubberDuckSessionFactory.RubberDuckSession.class,
            SdkRubberDuckSessionFactory.RubberDuckSession.class,
            AgentConfig.class,
            AgentConfig.class,
            String.class,
            int.class
        );
        method.setAccessible(true);
        RubberDuckDialogueExecutor executor = new RubberDuckDialogueExecutor(
            sdkFactory(),
            new ReviewSystemPromptFormatter()
        );
        return (List<DialogueRound>) method.invoke(executor, sessionA, sessionB, agentA, agentB, initialPrompt, rounds);
    }

    private static SdkRubberDuckSessionFactory sdkFactory() {
        var copilotConfig = new dev.logicojp.reviewer.infrastructure.config.CopilotConfig(null, null, 60, 10, 15);
        var copilotService = new CopilotService(
            new dev.logicojp.reviewer.infrastructure.auth.CopilotCliPathResolver(copilotConfig),
            new CopilotHealthProbe(copilotConfig),
            copilotConfig,
            new CopilotStartupErrorFormatter(),
            new CopilotClientStarter()
        );
        return new SdkRubberDuckSessionFactory(copilotService, 1, "2026-06-24-13-00-00");
    }

    private static AgentConfig agentConfig(String name, String model) {
        return AgentConfig.builder()
            .name(name)
            .displayName(name)
            .model(model)
            .systemPrompt("SYSTEM")
            .instruction("BASE INSTRUCTION")
            .outputFormat("## Output Format\n\n- finding")
            .focusAreas(List.of("quality"))
            .skills(List.of())
            .build();
    }

    private static final class RecordingSession implements SdkRubberDuckSessionFactory.RubberDuckSession {
        // LinkedList (not ArrayDeque) because this test feeds null responses through the session
        // to prove the executor converts them to empty content; ArrayDeque rejects null elements.
        private final LinkedList<String> responses;
        private final List<String> prompts = new ArrayList<>();

        private RecordingSession(List<String> responses) {
            this.responses = new LinkedList<>(responses);
        }

        @Override
        public String send(String prompt) {
            prompts.add(prompt);
            return responses.isEmpty() ? "" : responses.removeFirst();
        }

        @Override
        public void close() {
        }

        List<String> prompts() {
            return prompts;
        }
    }
}
