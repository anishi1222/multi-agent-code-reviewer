package dev.logicojp.reviewer.service;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.config.CopilotConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


@DisplayName("CopilotService")
class CopilotServiceTest {

    private static final String UNRESOLVED_TOKEN_PLACEHOLDER = "${GITHUB_TOKEN}";

    @Nested
    @DisplayName("初期状態")
    class InitialState {

        @Test
        @DisplayName("未初期化状態ではisInitializedがfalse")
        void isInitializedReturnsFalseBeforeInitialization() {
            var service = new CopilotService(new CopilotConfig(null, null, 0, 0, 0));

            Assertions.assertThat(service.isInitialized()).isFalse();
        }

        @Test
        @DisplayName("未初期化状態でgetClientは例外")
        void getClientThrowsWhenNotInitialized() {
            var service = new CopilotService(new CopilotConfig(null, null, 0, 0, 0));

            Assertions.assertThatThrownBy(service::getClient)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CopilotService not initialized. Call initialize() first.");
        }
    }

    @Nested
    @DisplayName("トークン判定")
    class TokenHandling {

        @Test
        @DisplayName("有効トークンのみshouldUseTokenがtrue")
        void shouldUseTokenAcceptsOnlyUsableToken() throws Exception {
            var service = new CopilotService(new CopilotConfig(null, null, 0, 0, 0));

            Assertions.assertThat((boolean) invokeInstance(service, "shouldUseToken", new Class[]{String.class}, "ghp_abc"))
                .isTrue();
            Assertions.assertThat((boolean) invokeInstance(service, "shouldUseToken", new Class[]{String.class}, ""))
                .isFalse();
            Assertions.assertThat((boolean) invokeInstance(service, "shouldUseToken", new Class[]{String.class}, "   "))
                .isFalse();
            Assertions.assertThat((boolean) invokeInstance(service, "shouldUseToken", new Class[]{String.class}, (Object) null))
                .isFalse();
            Assertions.assertThat((boolean) invokeInstance(service, "shouldUseToken", new Class[]{String.class},
                UNRESOLVED_TOKEN_PLACEHOLDER)).isFalse();
        }

        @Test
        @DisplayName("normalizeTokenは無効トークンをnullに正規化")
        void normalizeTokenConvertsInvalidValuesToNull() throws Exception {
            var service = new CopilotService(new CopilotConfig(null, null, 0, 0, 0));

            Assertions.assertThat((String) invokeInstance(service, "normalizeToken", new Class[]{String.class}, "ghp_ok"))
                .isEqualTo("ghp_ok");
            Assertions.assertThat((String) invokeInstance(service, "normalizeToken", new Class[]{String.class}, ""))
                .isNull();
            Assertions.assertThat((String) invokeInstance(service, "normalizeToken", new Class[]{String.class},
                UNRESOLVED_TOKEN_PLACEHOLDER)).isNull();
        }
    }

    @Nested
    @DisplayName("メッセージ生成")
    class MessageBuilders {

        @Test
        @DisplayName("短いフィンガープリントはそのまま返す")
        void shortFingerprintReturnsOriginalWhenShort() throws Exception {
            String value = (String) invokeStatic(CopilotService.class,
                "shortFingerprint", new Class[]{String.class}, "abc123");

            Assertions.assertThat(value).isEqualTo("abc123");
        }

        @Test
        @DisplayName("長いフィンガープリントは先頭12文字に切り詰め")
        void shortFingerprintTruncatesLongValue() throws Exception {
            String value = (String) invokeStatic(CopilotService.class,
                "shortFingerprint", new Class[]{String.class}, "1234567890abcdef");

            Assertions.assertThat(value).isEqualTo("1234567890ab");
        }

        @Test
        @DisplayName("nullまたは空のフィンガープリントはnone")
        void shortFingerprintReturnsNoneForBlank() throws Exception {
            Assertions.assertThat((String) invokeStatic(CopilotService.class,
                "shortFingerprint", new Class[]{String.class}, (Object) null)).isEqualTo("none");
            Assertions.assertThat((String) invokeStatic(CopilotService.class,
                "shortFingerprint", new Class[]{String.class}, "   ")).isEqualTo("none");
        }

        @Test
        @DisplayName("ExecutionExceptionがTimeoutException原因ならタイムアウト文言になる")
        void mapExecutionExceptionForTimeoutCause() throws Exception {
            var service = new CopilotService(new CopilotConfig(null, null, 0, 0, 0));
            var input = new ExecutionException(new TimeoutException("timeout"));

            var ex = (CopilotCliException) invokeInstance(
                service,
                "mapExecutionException",
                new Class[]{ExecutionException.class},
                input
            );

            Assertions.assertThat(ex.getMessage()).contains("Copilot CLI ping timed out");
        }

        @Test
        @DisplayName("ExecutionExceptionが一般原因なら原因メッセージを含む")
        void mapExecutionExceptionForGenericCause() throws Exception {
            var service = new CopilotService(new CopilotConfig(null, null, 0, 0, 0));
            var input = new ExecutionException(new IllegalStateException("boom"));

            var ex = (CopilotCliException) invokeInstance(
                service,
                "mapExecutionException",
                new Class[]{ExecutionException.class},
                input
            );

            Assertions.assertThat(ex.getMessage()).contains("Copilot client start failed: boom");
        }
    }

    private static Object invokeInstance(Object target,
                                         String methodName,
                                         Class<?>[] parameterTypes,
                                         Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object invokeStatic(Class<?> type,
                                       String methodName,
                                       Class<?>[] parameterTypes,
                                       Object... args) throws Exception {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
