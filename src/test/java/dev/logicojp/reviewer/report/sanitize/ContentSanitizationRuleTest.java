package dev.logicojp.reviewer.report.sanitize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentSanitizationRule")
class ContentSanitizationRuleTest {

    @Nested
    @DisplayName("apply")
    class Apply {

        @Test
        @DisplayName("パターンにマッチする部分を置換する")
        void replacesMatchingPattern() {
            var rule = new ContentSanitizationRule(Pattern.compile("foo"), "bar");

            assertThat(rule.apply("hello foo world")).isEqualTo("hello bar world");
        }

        @Test
        @DisplayName("複数のマッチを全て置換する")
        void replacesAllOccurrences() {
            var rule = new ContentSanitizationRule(Pattern.compile("x"), "y");

            assertThat(rule.apply("xaxbxc")).isEqualTo("yaybyc");
        }

        @Test
        @DisplayName("マッチしない場合は入力をそのまま返す")
        void returnsInputWhenNoMatch() {
            var rule = new ContentSanitizationRule(Pattern.compile("zzz"), "replacement");

            assertThat(rule.apply("no match here")).isEqualTo("no match here");
        }

        @Test
        @DisplayName("空文字列への置換でマッチ部分を削除する")
        void removesMatchWhenReplacementIsEmpty() {
            var rule = new ContentSanitizationRule(Pattern.compile("<thinking>.*?</thinking>", Pattern.DOTALL), "");

            assertThat(rule.apply("before<thinking>hidden</thinking>after")).isEqualTo("beforeafter");
        }

        @Test
        @DisplayName("空文字列入力に対して空文字列を返す")
        void handlesEmptyInput() {
            var rule = new ContentSanitizationRule(Pattern.compile("anything"), "replaced");

            assertThat(rule.apply("")).isEmpty();
        }

        @Test
        @DisplayName("fastCheckMarkersは大文字小文字を無視して判定する")
        void fastCheckMarkersIgnoreCase() {
            var rule = new ContentSanitizationRule(
                Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
                "",
                List.of("javascript")
            );

            assertThat(rule.apply("JAVASCRIPT:alert(1)")).isEqualTo("alert(1)");
        }
    }

    @Nested
    @DisplayName("レコードフィールド")
    class RecordFields {

        @Test
        @DisplayName("patternとreplacementにアクセスできる")
        void accessFields() {
            Pattern pattern = Pattern.compile("test");
            var rule = new ContentSanitizationRule(pattern, "replacement");

            assertThat(rule.pattern()).isSameAs(pattern);
            assertThat(rule.replacement()).isEqualTo("replacement");
        }
    }
}
