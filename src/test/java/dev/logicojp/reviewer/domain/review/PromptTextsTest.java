package dev.logicojp.reviewer.domain.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptTexts")
class PromptTextsTest {

    @Test
    @DisplayName("null値は空文字に正規化される")
    void normalizesNullValuesToEmptyString() {
        PromptTexts texts = new PromptTexts(null, null, null);

        assertThat(texts.focusAreasGuidance()).isEmpty();
        assertThat(texts.localSourceHeader()).isEmpty();
        assertThat(texts.localReviewResultRequest()).isEmpty();
    }

    @Test
    @DisplayName("指定値はそのまま保持される")
    void keepsProvidedValues() {
        PromptTexts texts = new PromptTexts("focus", "header", "request");

        assertThat(texts.focusAreasGuidance()).isEqualTo("focus");
        assertThat(texts.localSourceHeader()).isEqualTo("header");
        assertThat(texts.localReviewResultRequest()).isEqualTo("request");
    }
}
