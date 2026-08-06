package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/// Regression lock for the `RetryPolicyUtils` consolidation performed in T014.1.
///
/// Two copies of this helper (`shared` and `infrastructure.auth`) were merged and the
/// merge took the **union** of their transient-failure markers, widening retry behaviour
/// for both groups of callers. These tests pin the resulting contract so that:
///
///  1. no marker can be silently dropped by a later "cleanup" (behaviour narrowing), and
///  2. the widening cannot reclassify a hard failure the CLI must report as transient.
///
/// The boundedness half of the contract — that a widened classification costs bounded
/// extra attempts and never suppresses the final error — is proven in
/// [RetryWideningBoundednessTest].
@DisplayName("RetryPolicyUtils consolidation contract")
class RetryPolicyConsolidationTest {

    @Nested
    @DisplayName("統合後のマーカー集合(union)")
    class MarkerUnion {

        @ParameterizedTest(name = "[{index}] \"{0}\" は一時的障害")
        @ValueSource(strings = {
            // 統合前 shared 側のみが持っていたマーカー
            "temporarily", "rate limit", "too many requests", "429", "503", "network",
            // 統合前 infrastructure.auth 側のみが持っていたマーカー
            "unavailable", "stream closed", "broken pipe",
            // 両方のコピーが持っていたマーカー
            "timeout", "connection reset"
        })
        @DisplayName("両コピー由来の全マーカーが一時的障害として残っている")
        void everyMarkerFromBothOriginalCopiesRemainsTransient(String marker) {
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException(marker)))
                .as("marker \"%s\" was present in a pre-consolidation copy and must stay transient", marker)
                .isTrue();
        }

        @Test
        @DisplayName("マーカー判定は大文字小文字を区別しない")
        void markerMatchingIsCaseInsensitive() {
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException("Too Many Requests"))).isTrue();
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException("HTTP 503 Service Unavailable"))).isTrue();
        }

        @Test
        @DisplayName("どのマーカーも含まない障害は一時的障害ではない")
        void unmarkedFailureIsNotTransient() {
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException("agent definition is malformed"))).isFalse();
        }
    }

    @Nested
    @DisplayName("InterruptedExceptionガード")
    class InterruptGuard {

        /// The load-bearing test for the guard that existed in only one pre-consolidation
        /// copy. The assertion on the plain [RuntimeException] is a negative control: it
        /// proves the same message *is* transient, so the `false` verdict below can only
        /// come from the `instanceof InterruptedException` short-circuit and not from the
        /// message simply failing to match.
        @Test
        @DisplayName("一時的マーカーを含むメッセージでも割り込みはリトライしない")
        void interruptShortCircuitsAheadOfMessageMatching() {
            String messageThatWouldOtherwiseRetry = "connection reset";

            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException(messageThatWouldOtherwiseRetry)))
                .as("negative control: this message must be transient for the guard test to mean anything")
                .isTrue();

            assertThat(RetryPolicyUtils.isTransientException(new InterruptedException(messageThatWouldOtherwiseRetry)))
                .as("an interrupt is a cancellation signal and must never be retried")
                .isFalse();
        }

        @Test
        @DisplayName("ExecutionExceptionでラップされた割り込みもリトライしない")
        void wrappedInterruptIsNotTransient() {
            Exception wrapped = new ExecutionException(new InterruptedException("timeout while awaiting cancellation"));

            assertThat(RetryPolicyUtils.isTransientException(wrapped)).isFalse();
        }

        @Test
        @DisplayName("メッセージなしの割り込みもリトライしない")
        void bareInterruptIsNotTransient() {
            assertThat(RetryPolicyUtils.isTransientException(new InterruptedException())).isFalse();
        }
    }

    @Nested
    @DisplayName("null安全性")
    class NullSafety {

        /// The pre-consolidation `shared` copy dereferenced the root cause unguarded and
        /// would have thrown [NullPointerException] here.
        @Test
        @DisplayName("null例外はNPEではなくfalseを返す")
        void nullThrowableIsNotTransient() {
            assertThat(RetryPolicyUtils.isTransientException(null)).isFalse();
        }

        @Test
        @DisplayName("メッセージがnull/空白の例外は一時的障害ではない")
        void nullOrBlankMessageIsNotTransient() {
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException((String) null))).isFalse();
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException("   "))).isFalse();
        }
    }

    @Nested
    @DisplayName("恒久的障害は拡大後もリトライ対象にならない")
    class PermanentFailuresStayPermanent {

        @ParameterizedTest(name = "[{index}] \"{0}\" は一時的障害ではない")
        @ValueSource(strings = {
            "401 unauthorized",
            "403 forbidden",
            "404 not found",
            "invalid token supplied",
            "authentication failed",
            "invalid model: gpt-nonexistent",
            "bad request: malformed payload",
            "agent 'security' not found",
            "permission denied"
        })
        @DisplayName("認証・認可・入力不正はexceptionパスでもリトライされない")
        void hardFailuresAreNotTransient(String message) {
            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException(message))).isFalse();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" は再試行不可")
        @ValueSource(strings = {
            "401 unauthorized",
            "403 forbidden",
            "404 not found",
            "invalid token supplied",
            "authentication failed",
            "invalid model: gpt-nonexistent",
            "bad request: malformed payload"
        })
        @DisplayName("結果メッセージパスの拒否リストは統合後も有効")
        void hardFailuresAreNotRetryableOnResultPath(String message) {
            assertThat(RetryPolicyUtils.isRetryableFailureMessage(message)).isFalse();
        }
    }

    @Nested
    @DisplayName("既知の残存リスク: 部分文字列マーカーの誤検知")
    class SubstringCollisionCharacterisation {

        /// `containsAny` is a naive `String.contains` check, so the bare numeric markers
        /// `429` and `503` match anywhere in a message — including inside a line number,
        /// a path, or a model identifier. These cases are *characterised* rather than
        /// asserted as desirable: the accepted mitigation is that the retry budget is
        /// bounded and the original error is still surfaced, which
        /// [RetryWideningBoundednessTest] proves.
        @Test
        @DisplayName("数値マーカーは無関係な数字列にも一致する(誤検知)")
        void bareNumericMarkersCollideWithUnrelatedDigits() {
            assertThat(RetryPolicyUtils.isTransientException(
                new RuntimeException("agent config parse error at line 429")))
                .as("false positive: \"429\" matched a line number")
                .isTrue();

            assertThat(RetryPolicyUtils.isTransientException(
                new RuntimeException("cannot read /home/user/.cache/5031/agent.md")))
                .as("false positive: \"503\" matched a path segment")
                .isTrue();
        }

        @Test
        @DisplayName("\"network\"/\"unavailable\"は恒久的な設定エラーにも一致する(誤検知)")
        void wordMarkersCollideWithPermanentConfigurationErrors() {
            assertThat(RetryPolicyUtils.isTransientException(
                new RuntimeException("network policy denies access to this repository")))
                .as("false positive: permanent authorization failure matched \"network\"")
                .isTrue();

            assertThat(RetryPolicyUtils.isTransientException(
                new RuntimeException("agent 'security' is unavailable: file not found")))
                .as("false positive: permanent configuration failure matched \"unavailable\"")
                .isTrue();
        }

        /// The two gates are independent: [RetryPolicyUtils#isTransientException] guards
        /// the *exception* path and has no deny-list, while
        /// [RetryPolicyUtils#isRetryableFailureMessage] guards the *result-message* path
        /// and does. A message can therefore be transient on one path and non-retryable on
        /// the other; the deny-list is what keeps auth failures out of the retry budget on
        /// the result path.
        @Test
        @DisplayName("認証失敗に数値マーカーが混ざっても結果パスの拒否リストが優先される")
        void resultPathDenyListStillWinsOverNumericMarkers() {
            String authFailureCarryingTransientMarker = "401 unauthorized (rate limit headers present)";

            assertThat(RetryPolicyUtils.isTransientException(new RuntimeException(authFailureCarryingTransientMarker)))
                .as("exception path has no deny-list, so the transient marker wins there")
                .isTrue();

            assertThat(RetryPolicyUtils.isRetryableFailureMessage(authFailureCarryingTransientMarker))
                .as("result path deny-list must still reject the auth failure")
                .isFalse();
        }
    }

    @Nested
    @DisplayName("型ベース判定は統合後も維持されている")
    class TypeBasedClassification {

        @Test
        @DisplayName("TimeoutException/IOExceptionはメッセージに関係なく一時的障害")
        void timeoutAndIoAreTransientRegardlessOfMessage() {
            assertThat(RetryPolicyUtils.isTransientException(new TimeoutException("401 unauthorized"))).isTrue();
            assertThat(RetryPolicyUtils.isTransientException(new IOException("403 forbidden"))).isTrue();
        }
    }
}
