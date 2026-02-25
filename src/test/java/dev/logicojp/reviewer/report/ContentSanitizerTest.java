package dev.logicojp.reviewer.report;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


@DisplayName("ContentSanitizer")
class ContentSanitizerTest {

    @Nested
    @DisplayName("sanitize")
    class Sanitize {

        @Test
        @DisplayName("nullの場合はnullを返す")
        void returnsNullForNull() {
            Assertions.assertThat(ContentSanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("通常のコンテンツはそのまま返す")
        void passesNormalContent() {
            String input = "# Review\n\n## Findings\n\nNo issues found.";
            Assertions.assertThat(ContentSanitizer.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("thinkingブロックを除去する")
        void removesThinkingBlocks() {
            String input = "<thinking>Internal reasoning here</thinking>\n\nActual review content.";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("<thinking>");
            Assertions.assertThat(result).doesNotContain("Internal reasoning");
            Assertions.assertThat(result).contains("Actual review content.");
        }

        @Test
        @DisplayName("antThinkingブロックを除去する")
        void removesAntThinkingBlocks() {
            String input = "<antThinking>reasoning</antThinking>\nReview.";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("antThinking");
            Assertions.assertThat(result).contains("Review.");
        }

        @Test
        @DisplayName("scriptタグを除去する")
        void removesScriptTags() {
            String input = "Review <script>alert('xss')</script> content.";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("<script>");
            Assertions.assertThat(result).doesNotContain("alert");
        }

        @Test
        @DisplayName("iframeタグを除去する")
        void removesIframeTags() {
            String input = "Content <iframe src='evil.com'></iframe> here.";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("<iframe");
        }

        @Test
        @DisplayName("過剰な空行を2行に圧縮する")
        void collapsesExcessiveBlankLines() {
            String input = "Line 1\n\n\n\n\nLine 2";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).isEqualTo("Line 1\n\nLine 2");
        }

        @Test
        @DisplayName("javascriptプロトコルを除去する")
        void removesJavascriptProtocol() {
            String input = "Link: javascript:alert(1)";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("javascript:");
        }

        @Test
        @DisplayName("HTML数値エンティティでエンコードされたjavascriptプロトコルを除去する")
        void removesEncodedJavascriptProtocol() {
            String input = "<a href=\"jav&#97;script:alert(1)\">click</a>";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("javascript:");
            Assertions.assertThat(result).doesNotContain("alert(1)");
        }

        @Test
        @DisplayName("HTML数値エンティティでエンコードされたイベントハンドラを除去する")
        void removesEncodedEventHandler() {
            String input = "<img src=x on&#101;rror=alert(1)>";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("onerror=");
            Assertions.assertThat(result).doesNotContain("alert(1)");
        }

        @Test
        @DisplayName("名前付きHTMLエンティティでエンコードされたscriptタグを除去する")
        void removesNamedEntityEncodedScriptTag() {
            String input = "&lt;script&gt;alert(1)&lt;/script&gt;";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("<script>");
            Assertions.assertThat(result).doesNotContain("alert(1)");
        }

        @Test
        @DisplayName("名前付きHTMLエンティティでエンコードされたjavascriptプロトコルを除去する")
        void removesNamedEntityEncodedJavascriptProtocol() {
            String input = "&lt;a href=&quot;javascript:alert(1)&quot;&gt;x&lt;/a&gt;";
            String result = ContentSanitizer.sanitize(input);
            Assertions.assertThat(result).doesNotContain("javascript:");
            Assertions.assertThat(result).doesNotContain("alert(1)");
        }
    }
}
