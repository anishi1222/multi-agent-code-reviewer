package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptContentCompactor")
class PromptContentCompactorTest {

    @Test
    @DisplayName("上限以下の文字列はそのまま返す")
    void returnsOriginalWhenWithinBudget() {
        assertThat(PromptContentCompactor.compact("short", 10)).isEqualTo("short");
    }

    @Test
    @DisplayName("上限を超える文字列は省略マーカー付きで切り詰める")
    void compactsLongContent() {
        String compacted = PromptContentCompactor.compact("abcdefghijklmnopqrstuvwxyz", 20);

        assertThat(compacted).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("末尾保持モードでは末尾を残す")
    void compactsKeepingTail() {
        String compacted = PromptContentCompactor.compactKeepingTail("abcdefghijklmnopqrstuvwxyz", 20);

        assertThat(compacted).hasSizeLessThanOrEqualTo(20);
        assertThat(compacted).endsWith("uvwxyz");
    }

    @Test
    @DisplayName("ソースコードの途中で切り詰めてもコードフェンスを閉じる")
    void compactsSourceWithClosedCodeFence() {
        String source = """
            ### Main.java

            ```java
            class Main {
                void run() {
                    System.out.println("long source content");
                }
            }
            ```

            ### Other.java

            ```java
            class Other {}
            ```

            """;

        String compacted = PromptContentCompactor.compactSourceBlocks(source, 100);

        assertThat(compacted).hasSizeLessThanOrEqualTo(100);
        assertThat(compacted).contains("remaining source omitted");
        assertThat(compacted.lines().filter(line -> line.startsWith("```")).count()).isEven();
    }

    @Test
    @DisplayName("Markdown内部の短いフェンスを外側ブロック終端と誤認しない")
    void preservesOuterFenceWithNestedMarkdownFence() {
        String source = """
            ### README.md

            ````markdown
            Example:

            ```java
            class Main {}
            ```

            Additional explanation that exceeds the compact budget.
            ````

            """;

        String compacted = PromptContentCompactor.compactSourceBlocks(source, 120);

        assertThat(compacted).contains("remaining source omitted");
        assertThat(compacted).contains("\n````\n");
        assertThat(compacted).doesNotEndWith("```java");
    }

    @Test
    @DisplayName("切り詰め位置が開始フェンス直後でも閉じフェンスを追加する")
    void closesFenceAtOpeningLineBoundary() {
        String source = "### A.java\n\n```java\n" + "x".repeat(200) + "\n```\n\n";
        int openingLineEnd = source.indexOf('\n', source.indexOf("```java"));
        int maxChars = openingLineEnd
            + "\n```\n".length()
            + "\n\n... (remaining source omitted for token budget)\n".length();

        String compacted = PromptContentCompactor.compactSourceBlocks(source, maxChars);

        assertThat(compacted).contains("```java\n```\n");
        assertThat(compacted).contains("remaining source omitted");
    }
}
