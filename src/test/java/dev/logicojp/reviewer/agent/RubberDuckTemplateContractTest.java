package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rubber-duck template contract")
class RubberDuckTemplateContractTest {

    @Test
    @DisplayName("全対話テンプレートがGood Pointsを評価または統合する")
    void allDialogueTemplatesCoverGoodPoints() throws IOException {
        List<String> templates = List.of(
            "rubber-duck-initial-ja.md",
            "rubber-duck-peer-review-ja.md",
            "rubber-duck-counter-ja.md",
            "rubber-duck-synthesis-ja.md",
            "rubber-duck-initial-en.md",
            "rubber-duck-peer-review-en.md",
            "rubber-duck-counter-en.md",
            "rubber-duck-synthesis-en.md"
        );

        for (String template : templates) {
            String content = Files.readString(Path.of("templates", template));
            assertThat(content)
                .as(template)
                .contains("Good Points");
        }
    }
}
