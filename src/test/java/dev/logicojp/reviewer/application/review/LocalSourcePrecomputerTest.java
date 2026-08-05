package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.outbound.CollectLocalSourcePort;
import dev.logicojp.reviewer.domain.review.LocalFileCandidate;
import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalSourcePrecomputer")
class LocalSourcePrecomputerTest {

    @Test
    @DisplayName("GitHubターゲットでは事前収集を行わずOptional.emptyを返す")
    void returnsEmptyForGithubTarget() {
        var precomputer = new LocalSourcePrecomputer(new CollectLocalSourcePort() {
            @Override
            public List<LocalFileCandidate> collect(Path directory, LocalFileSelectionConfig config) {
                throw new IllegalStateException("should not be called");
            }

            @Override
            public String formatContent(List<LocalFileCandidate> candidates) {
                throw new IllegalStateException("should not be called");
            }
        });

        var result = precomputer.preComputeSourceContent(ReviewTarget.gitHub("owner/repo"), selectionConfig());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ローカルターゲットでは収集結果を整形した内容を返す")
    void returnsReviewContentForLocalTarget() {
        var collected = new LocalFileCandidate(Path.of("repo/src/App.java"), 100);
        var precomputer = new LocalSourcePrecomputer(new CollectLocalSourcePort() {
            @Override
            public List<LocalFileCandidate> collect(Path directory, LocalFileSelectionConfig config) {
                assertThat(directory).isEqualTo(Path.of("repo"));
                assertThat(config).isEqualTo(selectionConfig());
                return List.of(collected);
            }

            @Override
            public String formatContent(List<LocalFileCandidate> candidates) {
                assertThat(candidates).containsExactly(collected);
                return "SOURCE_CONTENT";
            }
        });

        var result = precomputer.preComputeSourceContent(ReviewTarget.local(Path.of("repo")), selectionConfig());

        assertThat(result).hasValue("SOURCE_CONTENT");
    }

    private LocalFileSelectionConfig selectionConfig() {
        return new LocalFileSelectionConfig(1024, 4096, Set.of("target"), Set.of("java"), Set.of(), Set.of());
    }
}
