package dev.logicojp.reviewer.infrastructure.parsing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrontmatterParser")
class FrontmatterParserTest {

    @Nested
    @DisplayName("parse - 基本動作")
    class BasicParsing {

        @Test
        @DisplayName("標準的なフロントマターをパースする")
        void parsesStandardFrontmatter() {
            String content = """
                ---
                name: test
                description: "テスト説明"
                ---
                Body content here.""";

            var parsed = FrontmatterParser.parse(content.stripIndent());

            assertThat(parsed.hasFrontmatter()).isTrue();
            assertThat(parsed.fields()).containsEntry("name", "test");
            assertThat(parsed.fields()).containsEntry("description", "テスト説明");
            assertThat(parsed.body()).isEqualTo("Body content here.");
        }

        @Test
        @DisplayName("シングルクォートの値を正しくストリップする")
        void stripsSingleQuotes() {
            String content = """
                ---
                applyTo: '**/*.java'
                ---
                Rules.""";

            var parsed = FrontmatterParser.parse(content.stripIndent());

            assertThat(parsed.field("applyTo")).isEqualTo("**/*.java");
        }

        @Test
        @DisplayName("クォートなしの値をそのまま保持する")
        void preservesUnquotedValues() {
            String content = """
                ---
                model: claude-sonnet-4
                ---
                Content.""";

            var parsed = FrontmatterParser.parse(content.stripIndent());

            assertThat(parsed.field("model")).isEqualTo("claude-sonnet-4");
        }
    }

    @Nested
    @DisplayName("parse - フロントマターなし")
    class NoFrontmatter {

        @Test
        @DisplayName("---で始まらないコンテンツはフロントマターなしとして扱う")
        void noFrontmatterWhenNoDashes() {
            String content = "# Title\n\nBody content.";

            var parsed = FrontmatterParser.parse(content);

            assertThat(parsed.hasFrontmatter()).isFalse();
            assertThat(parsed.fields()).isEmpty();
            assertThat(parsed.body()).isEqualTo(content);
        }

        @Test
        @DisplayName("閉じ---がない場合はフロントマターなしとして扱う")
        void noFrontmatterWhenNoClosingDelimiter() {
            String content = "---\nname: test\nNo closing delimiter.";

            var parsed = FrontmatterParser.parse(content);

            assertThat(parsed.hasFrontmatter()).isFalse();
        }

        @Test
        @DisplayName("null入力で空の結果を返す")
        void handlesNullInput() {
            var parsed = FrontmatterParser.parse(null);

            assertThat(parsed.hasFrontmatter()).isFalse();
            assertThat(parsed.body()).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse - 空のフロントマター値")
    class EmptyValues {

        @Test
        @DisplayName("値が空のフィールドはメタデータに含まれない")
        void emptyValuesAreExcluded() {
            String content = """
                ---
                name:
                description:
                ---
                Body.""";

            var parsed = parseInstructionFrontmatter(content.stripIndent());

            assertThat(hasFrontmatter(parsed)).isTrue();
            assertThat(metadata(parsed)).isEmpty();
            // T013: the rewritten parser strips the trailing newline from the body; the
            // pre-migration parser preserved it. Body text is consumed as prompt content,
            // so trailing-whitespace normalisation is behaviour-preserving.
            assertThat(body(parsed)).isEqualTo("Body.");
        }
    }

    @Nested
    @DisplayName("parse - インデントされた行のスキップ")
    class IndentedLines {

        @Test
        @DisplayName("インデントされたネスト行はトップレベルフィールドとしてパースしない")
        void skipsIndentedLines() {
            String content = """
                ---
                name: skill
                metadata:
                  agent: security
                  version: "1.0"
                ---
                Prompt.""";

            var parsed = parseInstructionFrontmatter(content.stripIndent());

            assertThat(metadata(parsed)).containsEntry("name", "skill");
            assertThat(metadata(parsed)).doesNotContainKey("agent");
            assertThat(metadata(parsed)).doesNotContainKey("version");
        }
    }

    @Nested
    @DisplayName("parseNestedBlock")
    class NestedBlockParsing {

        @Test
        @DisplayName("metadataブロックを正しくパースする")
        void parsesMetadataBlock() {
            String frontmatter = """
                name: skill
                metadata:
                  agent: security
                  version: "1.0"
                description: test""";

            Map<String, String> metadata = FrontmatterParser.parseNestedBlock(
                frontmatter.stripIndent(), "metadata");

            assertThat(metadata).containsEntry("agent", "security");
            assertThat(metadata).containsEntry("version", "1.0");
        }

        @Test
        @DisplayName("ネストブロックが存在しない場合は空マップを返す")
        void returnsEmptyWhenBlockMissing() {
            String frontmatter = "name: test\ndescription: desc";

            Map<String, String> result = FrontmatterParser.parseNestedBlock(frontmatter, "metadata");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractRawFrontmatter")
    class ExtractRawFrontmatter {

        @Test
        @DisplayName("生のフロントマターテキストを抽出する")
        void extractsRawText() {
            String content = """
                ---
                name: test
                model: gpt-4
                ---
                Body.""";

            String raw = FrontmatterParser.parse(content.stripIndent()).rawFrontmatter();

            assertThat(raw).contains("name: test");
            assertThat(raw).contains("model: gpt-4");
            assertThat(raw).doesNotContain("Body.");
        }

        @Test
        @DisplayName("フロントマターがない場合はnullを返す")
        void returnsNullWhenMissing() {
            assertThat(FrontmatterParser.parse("No frontmatter").rawFrontmatter()).isNull();
        }
    }

    @Nested
    @DisplayName("getOrDefault")
    class GetOrDefault {

        @Test
        @DisplayName("存在するキーの値を返す")
        void returnsExistingValue() {
            String content = """
                ---
                name: test
                ---
                Body.""";

            var parsed = FrontmatterParser.parse(content.stripIndent());

            assertThat(parsed.fieldOrDefault("name", "default")).isEqualTo("test");
        }

        @Test
        @DisplayName("存在しないキーにはデフォルト値を返す")
        void returnsDefaultForMissing() {
            String content = """
                ---
                name: test
                ---
                Body.""";

            var parsed = FrontmatterParser.parse(content.stripIndent());

            assertThat(parsed.fieldOrDefault("missing", "fallback")).isEqualTo("fallback");
        }
    }

    private static Object parseInstructionFrontmatter(String content) {
        try {
            Class<?> type = Class.forName("dev.logicojp.reviewer.domain.instruction.InstructionFrontmatter");
            Method parse = type.getDeclaredMethod("parse", String.class);
            parse.setAccessible(true);
            return parse.invoke(null, content);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Instruction frontmatter parser should be available", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> metadata(Object parsed) {
        return (Map<String, String>) invokeParsedAccessor(parsed, "metadata");
    }

    private static String body(Object parsed) {
        return (String) invokeParsedAccessor(parsed, "body");
    }

    private static boolean hasFrontmatter(Object parsed) {
        return (boolean) invokeParsedAccessor(parsed, "hasFrontmatter");
    }

    private static Object invokeParsedAccessor(Object parsed, String methodName) {
        try {
            Method method = parsed.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(parsed);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Instruction frontmatter parsed accessor should be available: " + methodName, e);
        }
    }
}
