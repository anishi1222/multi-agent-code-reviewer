package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.RubberDuckRequest;
import dev.logicojp.reviewer.application.port.outbound.RunRubberDuckSessionPort;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.agent.DialogueRound;
import dev.logicojp.reviewer.domain.report.ReviewResult;
import dev.logicojp.reviewer.domain.report.ReviewResultFactory;
import dev.logicojp.reviewer.domain.review.ReviewContext;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckDialogueRunner")
class RubberDuckDialogueRunnerTest {

    @Test
    @DisplayName("rounds分のA/B対話を実行して最終ラウンドを合成する")
    void conductsConfiguredRounds() {
        RecordingRubberDuckSession session = new RecordingRubberDuckSession(List.of(
            new DialogueRound(1, "model-a", "A1", "model-b", "B1"),
            new DialogueRound(2, "model-a", "A2", "model-b", "B2")
        ));
        RubberDuckDialogueRunner runner = new RubberDuckDialogueRunner(
            session,
            new InMemoryTemplates(),
            new ReviewResultFactory()
        );

        List<ReviewResult> results = runner.run(
            agent(),
            ReviewTarget.gitHub("owner/repo"),
            context(),
            2,
            List.of()
        );

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.success()).isTrue();
            assertThat(result.content()).contains("A2").contains("B2");
            assertThat(result.repository()).isEqualTo("owner/repo");
        });
        assertThat(session.request().rounds()).isEqualTo(2);
        assertThat(session.request().agentB().model()).isEqualTo("model-b");
        // T013: the pre-migration runner embedded ReviewContext#cachedSourceContent into the
        // prompt for *any* target. ReviewTargetInstructionResolver now resolves
        // localSourceContent to null for GitHubTarget by design, so local source must NOT leak
        // into a GitHub-repository review. Asserting the absence is stricter than the old
        // `.contains("SOURCE")` expectation it replaces.
        assertThat(session.request().initialPrompt()).contains("INITIAL").contains("instruction");
        assertThat(session.request().initialPrompt()).doesNotContain("SOURCE");
    }

    private ReviewContext context() {
        return ReviewContext.builder()
            .invocationTimestamp("2026-06-24-14-00-00")
            .maxRetries(0)
            .cachedSourceContent("SOURCE")
            .build();
    }

    private AgentConfig agent() {
        return AgentConfig.builder()
            .name("agent")
            .displayName("Agent")
            .model("model-a")
            .peerModel("model-b")
            .systemPrompt("system")
            .instruction("instruction")
            .focusAreas(List.of())
            .skills(List.of())
            .language("ja")
            .build();
    }

    private static final class InMemoryTemplates implements LoadTemplatePort {
        @Override
        public String render(String templateKey, Map<String, String> placeholders) {
            return loadRaw(templateKey);
        }

        @Override
        public String loadRaw(String templateKey) {
            if ("rubber-duck-initial-ja".equals(templateKey)) {
                return "INITIAL";
            }
            throw new IllegalStateException("Unexpected template: " + templateKey);
        }
    }

    private static final class RecordingRubberDuckSession implements RunRubberDuckSessionPort {
        private final List<DialogueRound> rounds;
        private RubberDuckRequest request;

        private RecordingRubberDuckSession(List<DialogueRound> rounds) {
            this.rounds = List.copyOf(rounds);
        }

        @Override
        public List<DialogueRound> executeDialogue(RubberDuckRequest request) {
            this.request = request;
            return rounds;
        }

        RubberDuckRequest request() {
            return request;
        }
    }
}
