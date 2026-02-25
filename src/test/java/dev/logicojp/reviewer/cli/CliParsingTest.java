package dev.logicojp.reviewer.cli;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;


@DisplayName("CliParsing")
class CliParsingTest {

    // ========================================================================
    // hasHelpFlag
    // ========================================================================
    @Nested
    @DisplayName("hasHelpFlag")
    class HasHelpFlagTest {

        @Test
        @DisplayName("nullの場合はfalse")
        void nullReturnsFalse() {
            Assertions.assertThat(CliParsing.hasHelpFlag(null)).isFalse();
        }

        @Test
        @DisplayName("空配列の場合はfalse")
        void emptyReturnsFalse() {
            Assertions.assertThat(CliParsing.hasHelpFlag(new String[]{})).isFalse();
        }

        @Test
        @DisplayName("-hフラグで検出される")
        void shortFlag() {
            Assertions.assertThat(CliParsing.hasHelpFlag(new String[]{"run", "-h"})).isTrue();
        }

        @Test
        @DisplayName("--helpフラグで検出される")
        void longFlag() {
            Assertions.assertThat(CliParsing.hasHelpFlag(new String[]{"--help"})).isTrue();
        }

        @Test
        @DisplayName("ヘルプフラグがない場合はfalse")
        void noHelpFlag() {
            Assertions.assertThat(CliParsing.hasHelpFlag(new String[]{"run", "--repo", "owner/repo"})).isFalse();
        }
    }

    // ========================================================================
    // readSingleValue
    // ========================================================================
    @Nested
    @DisplayName("readSingleValue")
    class ReadSingleValueTest {

        @Test
        @DisplayName("インライン値を取得する")
        void inlineValue() {
            String[] args = {"--repo=owner/repo"};
            var result = CliParsing.readSingleValue(args[0], args, 0, "--repo");
            Assertions.assertThat(result.value()).isEqualTo("owner/repo");
            Assertions.assertThat(result.newIndex()).isZero();
        }

        @Test
        @DisplayName("次の引数から値を取得する")
        void nextArgValue() {
            String[] args = {"--repo", "owner/repo"};
            var result = CliParsing.readSingleValue(args[0], args, 0, "--repo");
            Assertions.assertThat(result.value()).isEqualTo("owner/repo");
            Assertions.assertThat(result.newIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("値がない場合はCliValidationExceptionが投げられる")
        void missingValueThrows() {
            String[] args = {"--repo"};
            Assertions.assertThatThrownBy(() -> CliParsing.readSingleValue(args[0], args, 0, "--repo"))
                    .isInstanceOf(CliValidationException.class)
                    .hasMessageContaining("--repo");
        }

        @Test
        @DisplayName("次の引数がオプションの場合はCliValidationExceptionが投げられる")
        void nextArgIsOptionThrows() {
            String[] args = {"--repo", "--verbose"};
            Assertions.assertThatThrownBy(() -> CliParsing.readSingleValue(args[0], args, 0, "--repo"))
                    .isInstanceOf(CliValidationException.class);
        }
    }

    // ========================================================================
    // readMultiValues
    // ========================================================================
    @Nested
    @DisplayName("readMultiValues")
    class ReadMultiValuesTest {

        @Test
        @DisplayName("複数の値を収集する")
        void collectsMultipleValues() {
            String[] args = {"--agents", "security", "quality", "performance"};
            var result = CliParsing.readMultiValues(args[0], args, 0, "--agents");
            Assertions.assertThat(result.values()).containsExactly("security", "quality", "performance");
            Assertions.assertThat(result.newIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("インライン値と後続値を組み合わせる")
        void inlineAndFollowing() {
            String[] args = {"--agents=security", "quality"};
            var result = CliParsing.readMultiValues(args[0], args, 0, "--agents");
            Assertions.assertThat(result.values()).containsExactly("security", "quality");
        }

        @Test
        @DisplayName("値がない場合はCliValidationExceptionが投げられる")
        void noValuesThrows() {
            String[] args = {"--agents", "--verbose"};
            Assertions.assertThatThrownBy(() -> CliParsing.readMultiValues(args[0], args, 0, "--agents"))
                    .isInstanceOf(CliValidationException.class);
        }
    }

    // ========================================================================
    // splitComma
    // ========================================================================
    @Nested
    @DisplayName("splitComma")
    class SplitCommaTest {

        @Test
        @DisplayName("nullの場合は空リスト")
        void nullReturnsEmpty() {
            Assertions.assertThat(CliParsing.splitComma(null)).isEmpty();
        }

        @Test
        @DisplayName("空文字の場合は空リスト")
        void blankReturnsEmpty() {
            Assertions.assertThat(CliParsing.splitComma("  ")).isEmpty();
        }

        @Test
        @DisplayName("カンマ区切りで分割される")
        void splitsByComma() {
            Assertions.assertThat(CliParsing.splitComma("a, b , c"))
                    .containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("単一の値もリストに変換される")
        void singleValue() {
            Assertions.assertThat(CliParsing.splitComma("security"))
                    .containsExactly("security");
        }
    }

    // ========================================================================
    // readTokenWithWarning
    // ========================================================================
    @Nested
    @DisplayName("readTokenWithWarning")
    class ReadTokenWithWarningTest {

        @Test
        @DisplayName("'-'は許可される")
        void stdinSentinelAllowed() {
            Assertions.assertThat(CliParsing.readTokenWithWarning("-")).isEqualTo("-");
        }

        @Test
        @DisplayName("直接トークン値は拒否される")
        void directTokenRejected() {
            Assertions.assertThatThrownBy(() -> CliParsing.readTokenWithWarning("ghp_abc123"))
                    .isInstanceOf(CliValidationException.class)
                    .hasMessageContaining("security");
        }
    }

    // ========================================================================
    // readToken with TokenInput
    // ========================================================================
    @Nested
    @DisplayName("readToken")
    class ReadTokenTest {

        @Test
        @DisplayName("stdin入力からトークンを読み取る（passwordあり）")
        void readsFromPassword() {
            CliParsing.TokenInput input = new CliParsing.TokenInput() {
                @Override
                public char[] readPassword() {
                    return "ghp_test_token".toCharArray();
                }

                @Override
                public byte[] readStdin(int maxBytes) {
                    return new byte[0];
                }
            };

            Assertions.assertThat(CliParsing.readToken("-", input)).isEqualTo("ghp_test_token");
        }

        @Test
        @DisplayName("stdin入力からトークンを読み取る（passwordなし、stdinフォールバック）")
        void readsFromStdin() {
            CliParsing.TokenInput input = new CliParsing.TokenInput() {
                @Override
                public char[] readPassword() {
                    return null;
                }

                @Override
                public byte[] readStdin(int maxBytes) {
                    return "ghp_stdin_token\n".getBytes();
                }
            };

            Assertions.assertThat(CliParsing.readToken("-", input)).isEqualTo("ghp_stdin_token");
        }

        @Test
        @DisplayName("非センチネル値はそのまま返される")
        void nonSentinelPassedThrough() {
            Assertions.assertThat(CliParsing.readToken("some_value")).isEqualTo("some_value");
        }
    }

    // ========================================================================
    // CliValidationException
    // ========================================================================
    @Nested
    @DisplayName("CliValidationException")
    class CliValidationExceptionTest {

        @Test
        @DisplayName("showUsageフラグが保持される")
        void showUsagePreserved() {
            var ex = new CliValidationException("bad input", true);
            Assertions.assertThat(ex.showUsage()).isTrue();
            Assertions.assertThat(ex.getMessage()).isEqualTo("bad input");
        }

        @Test
        @DisplayName("nullメッセージは空文字列に変換される")
        void nullMessageDefaultsToEmpty() {
            var ex = new CliValidationException(null, false);
            Assertions.assertThat(ex.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("causeを持つコンストラクタ")
        void withCause() {
            var cause = new RuntimeException("root cause");
            var ex = new CliValidationException("wrapped", false, cause);
            Assertions.assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}
