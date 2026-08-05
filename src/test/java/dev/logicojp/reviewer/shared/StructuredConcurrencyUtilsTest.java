package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StructuredConcurrencyUtils")
class StructuredConcurrencyUtilsTest {

    @Nested
    @DisplayName("joinWithTimeout")
    class JoinWithTimeout {

        @Test
        @DisplayName("スコープが正常に完了する場合はタイムアウトしない")
        void completesNormally() throws Exception {
            try (var scope = StructuredTaskScope.<String>open()) {
                scope.fork(() -> "result");
                StructuredConcurrencyUtils.joinWithTimeout(scope, 5, TimeUnit.SECONDS);
                // No exception means success
            }
        }

        @Test
        @DisplayName("タイムアウト期間内に完了しない場合はTimeoutExceptionをスローする")
        void throwsTimeoutException() throws Exception {
            try (var scope = StructuredTaskScope.<String>open()) {
                scope.fork(() -> {
                    Thread.sleep(60_000);
                    return "late";
                });
                assertThatThrownBy(() ->
                    StructuredConcurrencyUtils.joinWithTimeout(scope, 200, TimeUnit.MILLISECONDS)
                ).isInstanceOf(TimeoutException.class);
                // joinUntil already satisfies the owner-join contract,
                // so scope.close() can proceed without an extra join()
            }
        }
    }
}
