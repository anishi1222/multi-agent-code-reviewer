package dev.logicojp.reviewer.performance;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.orchestrator.ReviewResultMerger;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.LocalFileProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("手動実行のパフォーマンスベンチ用")
@DisplayName("PerformanceBenchmark")
class PerformanceBenchmarkTest {

    @Test
    @DisplayName("ReviewResultMergerの大量マージ性能を計測する")
    void benchmarkReviewResultMerger() {
        List<ReviewResult> results = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            AgentConfig config = AgentConfig.builder()
                .name("agent-" + (i % 10))
                .displayName("Agent " + (i % 10))
                .build();
            results.add(ReviewResult.builder()
                .agentConfig(config)
                .repository("owner/repo")
                .success(true)
                .content(sampleFinding(i))
                .build());
        }

        Instant started = Instant.now();
        List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(results);
        Duration elapsed = Duration.between(started, Instant.now());

        assertThat(merged).hasSize(10);
        assertThat(elapsed.toMillis()).isLessThan(5000);
    }

    @Test
    @DisplayName("LocalFileProviderの大量ファイル収集性能を計測する")
    void benchmarkLocalFileProvider(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 300; i++) {
            Files.writeString(tempDir.resolve("File" + i + ".java"),
                "public class File" + i + " { int v = " + i + "; }");
        }

        LocalFileProvider provider = new LocalFileProvider(tempDir);
        Instant started = Instant.now();
        LocalFileProvider.CollectionResult result = provider.collectAndGenerate();
        Duration elapsed = Duration.between(started, Instant.now());

        assertThat(result.fileCount()).isPositive();
        assertThat(result.reviewContent()).contains("File0.java");
        assertThat(elapsed.toMillis()).isLessThan(5000);
    }

    private static String sampleFinding(int seed) {
        return """
            ### 1. Sample Finding %d

            | **Priority** | Medium |
            | **指摘の概要** | Sample summary %d |
            | **該当箇所** | src/main/java/Sample%d.java |

            details
            """.formatted(seed % 30, seed, seed % 100);
    }
}
