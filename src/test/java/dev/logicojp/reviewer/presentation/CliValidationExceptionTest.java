package dev.logicojp.reviewer.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CliValidationException")
class CliValidationExceptionTest {

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("メッセージとshowUsageフラグが設定される")
        void setsMessageAndFlag() {
            var exception = new CliValidationException("invalid option", true);

            assertThat(exception.getMessage()).isEqualTo("invalid option");
            assertThat(exception.showUsage()).isTrue();
        }

        @Test
        @DisplayName("showUsageがfalseの場合")
        void showUsageFalse() {
            var exception = new CliValidationException("error", false);

            assertThat(exception.showUsage()).isFalse();
        }

        @Test
        @DisplayName("nullメッセージは空文字列に正規化される")
        void nullMessageNormalized() {
            var exception = new CliValidationException(null, true);

            assertThat(exception.getMessage()).isEmpty();
        }
    }

    @Nested
    @DisplayName("RuntimeException継承")
    class Inheritance {

        @Test
        @DisplayName("RuntimeExceptionを継承している")
        void extendsRuntimeException() {
            var exception = new CliValidationException("test", false);

            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("原因チェーンコンストラクタ")
    class CauseChainConstructor {

        @Test
        @DisplayName("原因例外が保持される")
        void preservesCause() {
            var cause = new IllegalArgumentException("root cause");
            var exception = new CliValidationException("wrapped", true, cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getMessage()).isEqualTo("wrapped");
            assertThat(exception.showUsage()).isTrue();
        }

        @Test
        @DisplayName("nullメッセージでも原因例外が保持される")
        void preservesCauseWithNullMessage() {
            var cause = new RuntimeException("original");
            var exception = new CliValidationException(null, false, cause);

            assertThat(exception.getMessage()).isEmpty();
            assertThat(exception.getCause()).isSameAs(cause);
        }
    }
}
