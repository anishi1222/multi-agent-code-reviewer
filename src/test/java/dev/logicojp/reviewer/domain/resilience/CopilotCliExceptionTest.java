package dev.logicojp.reviewer.domain.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotCliException")
class CopilotCliExceptionTest {

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("メッセージのみのコンストラクタ")
        void messageOnly() {
            var ex = new CopilotCliException("CLI not found");
            assertThat(ex.getMessage()).isEqualTo("CLI not found");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("メッセージと原因のコンストラクタ")
        void messageAndCause() {
            var cause = new RuntimeException("underlying error");
            var ex = new CopilotCliException("CLI failed", cause);
            assertThat(ex.getMessage()).isEqualTo("CLI failed");
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("RuntimeExceptionを継承している")
        void extendsRuntimeException() {
            var ex = new CopilotCliException("test");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
