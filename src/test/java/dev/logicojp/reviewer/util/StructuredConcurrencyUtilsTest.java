package dev.logicojp.reviewer.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@DisplayName("StructuredConcurrencyUtils")
class StructuredConcurrencyUtilsTest {

    @Test
    @DisplayName("join がタイムアウト内に完了すれば例外を送出しない")
    void joinWithinTimeoutSucceeds() {
        Assertions.assertThatCode(() -> {
            try (var scope = StructuredTaskScope.<Integer>open()) {
                scope.fork(() -> 1);
                StructuredConcurrencyUtils.joinWithTimeout(scope, 1, TimeUnit.SECONDS);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("join が期限超過した場合は TimeoutException を送出する")
    void joinTimeoutThrowsTimeoutException() {
        Assertions.assertThatThrownBy(() -> {
            try (var scope = StructuredTaskScope.<Integer>open()) {
                scope.fork(() -> {
                    Thread.sleep(300);
                    return 1;
                });
                StructuredConcurrencyUtils.joinWithTimeout(scope, 50, TimeUnit.MILLISECONDS);
            }
        }).isInstanceOf(TimeoutException.class)
            .hasMessageContaining("Join timed out after 50 milliseconds");
    }
}
