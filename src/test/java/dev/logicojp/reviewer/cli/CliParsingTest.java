package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CliParsing")
class CliParsingTest {

    @Nested
    @DisplayName("readSingleValue")
    class ReadSingleValue {

        @Test
        @DisplayName("次の引数から値を読み取る")
        void readsNextArgument() {
            String[] args = {"--repo", "owner/repo", "--all"};
            var result = CliParsing.readSingleValue("--repo", args, 0, "--repo");

            assertThat(result.value()).isEqualTo("owner/repo");
            assertThat(result.newIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("インライン値（--repo=owner/repo）を読み取る")
        void readsInlineValue() {
            String[] args = {"--repo=owner/repo", "--all"};
            var result = CliParsing.readSingleValue("--repo=owner/repo", args, 0, "--repo");

            assertThat(result.value()).isEqualTo("owner/repo");
            assertThat(result.newIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("値がない場合はCliValidationExceptionをスローする")
        void throwsWhenNoValue() {
            String[] args = {"--repo"};
            assertThatThrownBy(() -> CliParsing.readSingleValue("--repo", args, 0, "--repo"))
                .isInstanceOf(CliValidationException.class)
                .hasMessageContaining("requires a value");
        }

        @Test
        @DisplayName("値がオプションフラグで始まる場合はエラーとする")
        void throwsWhenValueIsOption() {
            String[] args = {"--repo", "--all"};
            assertThatThrownBy(() -> CliParsing.readSingleValue("--repo", args, 0, "--repo"))
                .isInstanceOf(CliValidationException.class);
        }
    }

    @Nested
    @DisplayName("readMultiValues")
    class ReadMultiValues {

        @Test
        @DisplayName("複数の値を読み取る")
        void readsMultipleValues() {
            String[] args = {"--agents-dir", "dir1", "dir2", "--all"};
            var result = CliParsing.readMultiValues("--agents-dir", args, 0, "--agents-dir");

            assertThat(result.values()).containsExactly("dir1", "dir2");
            assertThat(result.newIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("インライン値を含む複数値を読み取る")
        void readsInlineWithAdditional() {
            String[] args = {"--agents-dir=dir1", "dir2"};
            var result = CliParsing.readMultiValues("--agents-dir=dir1", args, 0, "--agents-dir");

            assertThat(result.values()).containsExactly("dir1", "dir2");
        }

        @Test
        @DisplayName("値がない場合はCliValidationExceptionをスローする")
        void throwsWhenNoValues() {
            String[] args = {"--agents-dir"};
            assertThatThrownBy(() -> CliParsing.readMultiValues("--agents-dir", args, 0, "--agents-dir"))
                .isInstanceOf(CliValidationException.class)
                .hasMessageContaining("requires at least one value");
        }
    }

    @Nested
    @DisplayName("splitComma")
    class SplitComma {

        @Test
        @DisplayName("カンマ区切りの値を分割する")
        void splitsCommaValues() {
            List<String> result = CliParsing.splitComma("a,b,c");
            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("空白をトリムする")
        void trimsWhitespace() {
            List<String> result = CliParsing.splitComma(" a , b , c ");
            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("空の値を除外する")
        void excludesEmptyParts() {
            List<String> result = CliParsing.splitComma("a,,b,");
            assertThat(result).containsExactly("a", "b");
        }

        @Test
        @DisplayName("null入力で空リストを返す")
        void returnsEmptyForNull() {
            assertThat(CliParsing.splitComma(null)).isEmpty();
        }

        @Test
        @DisplayName("空文字入力で空リストを返す")
        void returnsEmptyForBlank() {
            assertThat(CliParsing.splitComma("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasHelpFlag")
    class HasHelpFlag {

        @Test
        @DisplayName("-hフラグを検出する")
        void detectsShortHelp() {
            assertThat(CliParsing.hasHelpFlag(new String[]{"-h"})).isTrue();
        }

        @Test
        @DisplayName("--helpフラグを検出する")
        void detectsLongHelp() {
            assertThat(CliParsing.hasHelpFlag(new String[]{"--help"})).isTrue();
        }

        @Test
        @DisplayName("ヘルプフラグがない場合はfalseを返す")
        void returnsFalseWhenAbsent() {
            assertThat(CliParsing.hasHelpFlag(new String[]{"--all", "--repo", "test"})).isFalse();
        }

        @Test
        @DisplayName("null配列の場合はfalseを返す")
        void returnsFalseForNull() {
            assertThat(CliParsing.hasHelpFlag(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("readTokenWithWarning")
    class ReadTokenWithWarning {

        @Test
        @DisplayName("stdinセンチネル '-' は許可される")
        void acceptsStdinSentinel() {
            assertThat(CliParsing.readTokenWithWarning("-")).isEqualTo("-");
        }

        @Test
        @DisplayName("直接トークン指定は拒否される")
        void rejectsDirectToken() {
            assertThatThrownBy(() -> CliParsing.readTokenWithWarning("ghp_secret"))
                .isInstanceOf(CliValidationException.class)
                .hasMessageContaining("Direct token passing via command line is not supported");
        }
    }

    @Nested
    @DisplayName("readTokenChars")
    class ReadTokenChars {

        @Test
        @DisplayName("標準入力センチネルでパスワード入力を読み取る")
        void readsPasswordInputWhenAvailable() {
            CliParsing.TokenInput tokenInput = new CliParsing.TokenInput() {
                @Override
                public char[] readPassword() {
                    return " ghp_from_password ".toCharArray();
                }

                @Override
                public byte[] readStdin(int maxBytes) {
                    return "ignored".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            };

            char[] token = CliParsing.readTokenChars("-", tokenInput);
            try {
                assertThat(new String(token)).isEqualTo("ghp_from_password");
            } finally {
                Arrays.fill(token, '\0');
            }
        }

        @Test
        @DisplayName("パスワード入力が無い場合はstdinから読み取る")
        void fallsBackToStdinWhenPasswordUnavailable() {
            CliParsing.TokenInput tokenInput = new CliParsing.TokenInput() {
                @Override
                public char[] readPassword() {
                    return null;
                }

                @Override
                public byte[] readStdin(int maxBytes) {
                    return " ghp_from_stdin \n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            };

            char[] token = CliParsing.readTokenChars("-", tokenInput);
            try {
                assertThat(new String(token)).isEqualTo("ghp_from_stdin");
            } finally {
                Arrays.fill(token, '\0');
            }
        }
    }
}
