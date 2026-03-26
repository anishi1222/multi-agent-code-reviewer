package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.ModelConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewPreparationService")
class ReviewPreparationServiceTest {

    @Test
    @DisplayName("outputDirectory計算・banner表示を一括で行う")
    void preparesOutputDirectoryAndBanner() {
        AtomicBoolean bannerCalled = new AtomicBoolean(false);
        AtomicReference<Path> bannerOutputDirectory = new AtomicReference<>();

        var service = new ReviewPreparationService(
            (agentConfigs, agentDirs, modelConfig, target, outputDirectory, reviewModel) -> {
                bannerCalled.set(true);
                bannerOutputDirectory.set(outputDirectory);
            },
            Clock.fixed(Instant.parse("2026-02-19T09:10:11Z"), ZoneId.of("UTC"))
        );

        ReviewCommand.ParsedOptions options = ReviewCommand.ParsedOptions.builder()
            .target(new ReviewCommand.TargetSelection.Repository("owner/repo"))
            .agents(new ReviewCommand.AgentSelection.All())
            .outputDirectory(Path.of("./reports"))
            .additionalAgentDirs(List.of())
            .parallelism(4)
            .noSummary(false)
            .reviewModel("review-model")
            .reportModel("report-model")
            .summaryModel("summary-model")
            .defaultModel("default-model")
            .trustTarget(false)
            .build();
        ReviewTarget target = ReviewTarget.gitHub("owner/repo");
        ModelConfig modelConfig = new ModelConfig("r", "p", "s", "high", "d");
        Map<String, AgentConfig> agentConfigs = Map.of(
            "code-quality", new AgentConfig("code-quality", "Code Quality", "r", "prompt", "instruction", "", List.of(), List.of())
        );

        ReviewPreparationService.PreparedData prepared = service.prepare(
            options,
            target,
            modelConfig,
            agentConfigs,
            List.of(Path.of("agents"))
        );

        assertThat(bannerCalled.get()).isTrue();
        assertThat(prepared.outputDirectory()).isEqualTo(Path.of("./reports/owner/repo/2026-02-19-09-10-11"));
        assertThat(bannerOutputDirectory.get()).isEqualTo(Path.of("./reports/owner/repo/2026-02-19-09-10-11"));
    }

    @Test
    @DisplayName("ローカルターゲットがルートディレクトリでも出力先はベース配下に固定される")
    void keepsRootLocalTargetOutputInsideBaseDirectory() {
        AtomicReference<Path> bannerOutputDirectory = new AtomicReference<>();

        var service = new ReviewPreparationService(
            (agentConfigs, agentDirs, modelConfig, target, outputDirectory, reviewModel) ->
                bannerOutputDirectory.set(outputDirectory),
            Clock.fixed(Instant.parse("2026-02-19T09:10:11Z"), ZoneId.of("UTC"))
        );

        ReviewCommand.ParsedOptions options = ReviewCommand.ParsedOptions.builder()
            .target(new ReviewCommand.TargetSelection.LocalDirectory(Path.of("/")))
            .agents(new ReviewCommand.AgentSelection.All())
            .outputDirectory(Path.of("./reports"))
            .additionalAgentDirs(List.of())
            .parallelism(4)
            .noSummary(false)
            .reviewModel("review-model")
            .reportModel("report-model")
            .summaryModel("summary-model")
            .defaultModel("default-model")
            .trustTarget(false)
            .build();
        ReviewTarget target = ReviewTarget.local(Path.of("/"));
        ModelConfig modelConfig = new ModelConfig("r", "p", "s", "high", "d");

        ReviewPreparationService.PreparedData prepared = service.prepare(
            options,
            target,
            modelConfig,
            Map.of(),
            List.of()
        );

        assertThat(prepared.outputDirectory()).isEqualTo(Path.of("./reports/local-root/2026-02-19-09-10-11"));
        assertThat(bannerOutputDirectory.get()).isEqualTo(Path.of("./reports/local-root/2026-02-19-09-10-11"));
    }
}
