package dev.logicojp.reviewer.orchestrator;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("ReviewFindingDeduplicator")
class ReviewFindingDeduplicatorTest {

    @Test
    @DisplayName("同一findingはパス番号を集約して1件にマージされる")
    void mergesSameFindingAcrossPasses() {
        var deduplicator = new ReviewFindingDeduplicator();

        deduplicator.processPassResult(successResult(findingContent("SQLインジェクション")), 1);
        deduplicator.processPassResult(successResult(findingContent("SQLインジェクション")), 2);

        var findings = deduplicator.aggregatedFindings();
        Assertions.assertThat(findings).hasSize(1);
        var aggregated = findings.values().iterator().next();
        Assertions.assertThat(aggregated.passNumbers()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("findingヘッダが無い結果はfallbackとして重複排除される")
    void deduplicatesFallbackContents() {
        var deduplicator = new ReviewFindingDeduplicator();

        deduplicator.processPassResult(successResult("問題は見つかりませんでした。"), 1);
        deduplicator.processPassResult(successResult("問題は見つかりませんでした。"), 2);

        Assertions.assertThat(deduplicator.aggregatedFindings()).hasSize(1);
    }

    private static ReviewResult successResult(String content) {
        return ReviewResult.builder()
            .agentConfig(AgentConfig.builder().name("security").displayName("Security").build())
            .repository("owner/repo")
            .content(content)
            .success(true)
            .build();
    }

    private static String findingContent(String title) {
        return """
            ### 1. %s

            | **Priority** | High |
            | **指摘の概要** | パラメータ化されていないクエリ |
            | **該当箇所** | UserDao.java |

            詳細
            """.formatted(title);
    }
}