package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.LocalFileConfig;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalSourcePrecomputer")
class LocalSourcePrecomputerTest {

    @Test
    @DisplayName("GitHubターゲットでは事前収集を行わずOptional.emptyを返す")
    void returnsEmptyForGithubTarget() {
        var precomputer = new LocalSourcePrecomputer(
            (directory, config) -> () -> {
                throw new IllegalStateException("should not be called");
            },
            new LocalFileConfig()
        );

        var result = precomputer.preComputeSourceContent(ReviewTarget.gitHub("owner/repo"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ローカルターゲットでは収集結果のreviewContentを返す")
    void returnsReviewContentForLocalTarget() {
        var precomputer = new LocalSourcePrecomputer(
            (directory, config) -> () -> new dev.logicojp.reviewer.target.LocalFileProvider.CollectionResult(
                "SOURCE_CONTENT",
                "summary",
                2,
                100
            ),
            new LocalFileConfig()
        );

        var result = precomputer.preComputeSourceContent(ReviewTarget.local(Path.of("/tmp/repo")));

        assertThat(result).hasValue("SOURCE_CONTENT");
    }
}