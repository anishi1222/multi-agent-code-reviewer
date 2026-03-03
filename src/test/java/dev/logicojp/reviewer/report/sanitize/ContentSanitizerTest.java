package dev.logicojp.reviewer.report.sanitize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentSanitizer")
class ContentSanitizerTest {

    @Nested
    @DisplayName("CoTブロック除去")
    class ThinkingBlockRemoval {

        @Test
        @DisplayName("thinkingブロックを除去する")
        void removesThinkingBlock() {
            String input = "Before <thinking>This is internal reasoning</thinking> After";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("Before  After");
        }

        @Test
        @DisplayName("antThinkingブロックを除去する")
        void removesAntThinkingBlock() {
            String input = "Start <antThinking>Deep thought here</antThinking> End";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("Start  End");
        }

        @Test
        @DisplayName("reflectionブロックを除去する")
        void removesReflectionBlock() {
            String input = "A <reflection>Let me reconsider</reflection> B";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A  B");
        }

        @Test
        @DisplayName("複数行のthinkingブロックを除去する")
        void removesMultilineThinkingBlock() {
            String input = """
                Review result:
                <thinking>
                Let me think about this...
                Multiple lines of reasoning.
                </thinking>
                ### Finding 1""";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<thinking>");
            assertThat(result).contains("### Finding 1");
        }

        @Test
        @DisplayName("大文字小文字を区別しない")
        void caseInsensitive() {
            String input = "A <THINKING>LOUD THOUGHTS</THINKING> B";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A  B");
        }
    }

    @Nested
    @DisplayName("detailsブロック除去")
    class DetailsRemoval {

        @Test
        @DisplayName("Thinkingサマリーのdetailsブロックを除去する")
        void removesThinkingDetails() {
            String input = """
                Before
                <details><summary>Thinking</summary>Hidden reasoning</details>
                After""";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<details>");
            assertThat(result).contains("Before");
            assertThat(result).contains("After");
        }

        @Test
        @DisplayName("思考サマリーのdetailsブロックを除去する")
        void removesJapaneseThinkingDetails() {
            String input = """
                前
                <details><summary>思考プロセス</summary>推論内容</details>
                後""";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<details>");
        }
    }

    @Nested
    @DisplayName("空白行の正規化")
    class BlankLineNormalization {

        @Test
        @DisplayName("3行以上の連続空行を2行に圧縮する")
        void collapsesExcessiveBlankLines() {
            String input = "A\n\n\n\n\nB";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A\n\nB");
        }

        @Test
        @DisplayName("2行の空行はそのまま保持する")
        void preservesDoubleBlankLines() {
            String input = "A\n\nB";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("A\n\nB");
        }
    }

    @Nested
    @DisplayName("危険なHTML除去")
    class DangerousHtmlRemoval {

        @Test
        @DisplayName("scriptタグの開始タグを除去する")
        void removesScriptTags() {
            String input = "Before <script>alert('xss')</script> After";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("After");
        }

        @Test
        @DisplayName("iframeタグの開始タグを除去する")
        void removesIframeTags() {
            String input = "Before <iframe src=\"evil.html\"></iframe> After";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<iframe");
            assertThat(result).contains("After");
        }

        @Test
        @DisplayName("自己閉じタグを除去する")
        void removesSelfClosingTags() {
            String input = "Before <input type=\"hidden\" value=\"x\"/> After";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo("Before  After");
        }

        @Test
        @DisplayName("onclickイベントハンドラを除去する")
        void removesEventHandlers() {
            String input = "Before <img onclick=\"alert(1)\" src=\"safe.png\"> After";
            assertThat(ContentSanitizer.sanitize(input)).doesNotContain("onclick");
        }

        @Test
        @DisplayName("javascript: URIスキームを除去する")
        void removesJavascriptUri() {
            String input = "<a href=\"javascript: void(0)\">Click</a>";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("href=");
            assertThat(result).doesNotContain("javascript");
        }

        @Test
        @DisplayName("HTMLエンティティでエンコードされたscriptタグを除去する")
        void removesEntityEncodedScriptTags() {
            String input = "Before &#x3c;script&#x3e;alert(1)&#x3c;/script&#x3e; After";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("After");
        }

        @Test
        @DisplayName("vbscript: URIスキームを除去する")
        void removesVbscriptUri() {
            String input = "<img src=\"vbscript:msgbox('xss')\">";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("src=");
            assertThat(result).doesNotContain("vbscript");
        }

        @Test
        @DisplayName("data: base64 URIを除去する")
        void removesDataUri() {
            String input = "Before data:text/html;base64,PHNjcmlwdD4= After";
            assertThat(ContentSanitizer.sanitize(input)).doesNotContain("base64");
        }

        @Test
        @DisplayName("dangerous hrefを持つaタグを除去する")
        void removesAnchorWithDangerousHref() {
            String input = "Before <a href=\"javascript:alert(1)\">Click</a> After";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("<a");
            assertThat(result).doesNotContain("javascript");
            assertThat(result).contains("After");
        }

        @Test
        @DisplayName("空白混在のscript URI属性を除去する")
        void removesWhitespaceObfuscatedScriptUriAttribute() {
            String input = "<img src=\"j a v a s c r i p t:alert(1)\">";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContain("src=");
            assertThat(result).doesNotContain("javascript");
        }

        @Test
        @DisplayName("大文字小文字を区別しないHTMLタグ除去")
        void caseInsensitiveHtmlRemoval() {
            String input = "Before <SCRIPT>alert('x')</SCRIPT> After";
            String result = ContentSanitizer.sanitize(input);
            assertThat(result).doesNotContainIgnoringCase("<script>");
            assertThat(result).contains("After");
        }
    }

    @Nested
    @DisplayName("エッジケース")
    class EdgeCases {

        @Test
        @DisplayName("nullを入力するとnullを返す")
        void returnsNullForNull() {
            assertThat(ContentSanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("空文字を入力すると空文字を返す")
        void returnsEmptyForEmpty() {
            assertThat(ContentSanitizer.sanitize("")).isEmpty();
        }

        @Test
        @DisplayName("サニタイズ不要のコンテンツはそのまま返す")
        void preservesCleanContent() {
            String input = "### Finding 1\n\n| Priority | High |";
            assertThat(ContentSanitizer.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("差し替え戦略を利用してsanitizeできる")
        void usesInjectedSanitizationStrategy() {
            var strategyCalled = new AtomicBoolean(false);

            String result = ContentSanitizer.sanitize("input", content -> {
                strategyCalled.set(true);
                return "  INJECTED  ";
            });

            assertThat(strategyCalled).isTrue();
            assertThat(result).isEqualTo("INJECTED");
        }
    }
}
