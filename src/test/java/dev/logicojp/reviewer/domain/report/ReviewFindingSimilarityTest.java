package dev.logicojp.reviewer.domain.report;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewFindingSimilarity")
class ReviewFindingSimilarityTest {

    @Test
    @DisplayName("normalizeTextは記号と余分な空白を正規化する")
    void normalizeTextRemovesMarkersAndCollapsesWhitespace() {
        String normalized = ReviewFindingSimilarity.normalizeText("  **Path** | src/Main.java  ");
        assertThat(normalized).isEqualTo("path src main.java");
    }

    @Test
    @DisplayName("isSimilarTextは高い類似度のテキストを真と判定する")
    void isSimilarTextReturnsTrueForHighlySimilarText() {
        String left = ReviewFindingSimilarity.normalizeText("ReviewCommand class has too many responsibilities");
        String right = ReviewFindingSimilarity.normalizeText("ReviewCommand class has many responsibilities");

        boolean similar = ReviewFindingSimilarity.isSimilarText(
            left,
            right,
            ReviewFindingSimilarity.bigrams(left),
            ReviewFindingSimilarity.bigrams(right)
        );

        assertThat(similar).isTrue();
    }

    @Test
    @DisplayName("hasCommonKeywordは共有キーワードがある場合に真となる")
    void hasCommonKeywordReturnsTrueWhenAnyTokenMatches() {
        boolean result = ReviewFindingSimilarity.hasCommonKeyword(
            "reviewcommand responsibilities",
            "reviewcommand complexity"
        );
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("bigramsは2文字単位の集合を返す")
    void bigramsReturnsAdjacentCharacterPairs() {
        Set<String> grams = ReviewFindingSimilarity.bigrams("abc");
        assertThat(grams).containsExactlyInAnyOrder("ab", "bc");
    }
}