package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.application.port.inbound.ReviewPlan;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewPreparationService")
class ReviewPreparationServiceTest {

    @Test
    @DisplayName("エージェントが存在する場合はbanner表示まで完了する")
    void preparesBannerWhenAgentsExist() {
        var service = new ReviewPreparationService(formatter(), () -> new ReviewPlan(1));
        Map<String, AgentConfig> agentConfigs = Map.of(
            "code-quality", new AgentConfig("code-quality", "Code Quality", "r", "prompt", "instruction", "", List.of(), List.of())
        );

        assertThatCode(() -> service.prepare(
            List.of(Path.of("agents")),
            agentConfigs,
            ReviewTarget.gitHub("owner/repo"),
            Path.of("./reports/owner/repo"),
            "summary-model",
            "review-model"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("エージェントが空の場合は検証エラー")
    void throwsWhenAgentsAreEmpty() {
        var service = new ReviewPreparationService(formatter(), () -> new ReviewPlan(1));

        assertThatThrownBy(() -> service.prepare(
            List.of(Path.of("agents")),
            Map.of(),
            ReviewTarget.local(Path.of("/")),
            Path.of("./reports/local-root"),
            "summary-model",
            "review-model"
        ))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("No review agents found");
    }

    // removed: preparesOutputDirectoryAndBanner output-directory assertions - timestamped output-directory calculation no longer exists in ReviewPreparationService.
    // removed: keepsRootLocalTargetOutputInsideBaseDirectory - local-root output-directory calculation moved out of ReviewPreparationService; local root naming is represented by ReviewTarget.repositorySubPath().

    private static ReviewOutputFormatter formatter() {
        CliOutput cliOutput = new CliOutput(
            new PrintStream(OutputStream.nullOutputStream()),
            new PrintStream(OutputStream.nullOutputStream())
        );
        return new ReviewOutputFormatter(cliOutput);
    }
}
