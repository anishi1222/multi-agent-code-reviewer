package dev.logicojp.reviewer.domain.report;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewMergedContentFormatter")
class ReviewMergedContentFormatterTest {

    @Test
    @DisplayName("findingsが空の場合は指摘事項なしを返す")
    void returnsNoFindingsWhenEmpty() {
        String content = ReviewMergedContentFormatter.format(Map.of(), 3, 0);
        assertThat(content).isEqualTo("指摘事項なし");
    }

    @Test
    @DisplayName("複数findingを番号付きで連結し重複パス情報を表示する")
    void formatsFindingsWithPassInformation() {
        Map<String, AggregatedFinding> findings = new LinkedHashMap<>();
        findings.put("k1", finding("SQLインジェクション", "body-1", new LinkedHashSet<>(List.of(1, 2))));
        findings.put("k2", finding("N+1", "body-2", new LinkedHashSet<>(List.of(3))));

        String content = ReviewMergedContentFormatter.format(findings, 3, 0);

        assertThat(content).contains("### 1. SQLインジェクション");
        assertThat(content).contains("> 検出パス: 1, 2");
        assertThat(content).contains("### 2. N+1");
        assertThat(content).contains("body-1");
        assertThat(content).contains("body-2");
    }

    @Test
    @DisplayName("失敗パスがある場合は末尾に注記を付与する")
    void appendsFailureNoteWhenFailedPassesExist() {
        Map<String, AggregatedFinding> findings = new LinkedHashMap<>();
        findings.put("k", finding("タイトル", "本文", Set.of(1)));

        String content = ReviewMergedContentFormatter.format(findings, 4, 1);

        assertThat(content).contains("**注記**");
        assertThat(content).contains("4 パス中 1 パスが失敗しました");
    }

    private AggregatedFinding finding(String title, String body, Set<Integer> passNumbers) {
        return new AggregatedFinding(
            title,
            body,
            new LinkedHashSet<>(passNumbers),
            new AggregatedFinding.NormalizedFinding(
                "title",
                "high",
                "summary",
                "location",
                Set.of("title"),
                Set.of("ti"),
                Set.of("su"),
                Set.of("lo")
            )
        );
    }
}