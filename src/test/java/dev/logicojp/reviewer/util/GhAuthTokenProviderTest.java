package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GhAuthTokenProvider")
class GhAuthTokenProviderTest {

    @Test
    @DisplayName("gh pathが解決できない場合はcommandを実行せずemptyを返す")
    void returnsEmptyWhenGhPathMissing() {
        AtomicInteger calls = new AtomicInteger();
        var provider = new GhAuthTokenProvider(
            5,
            () -> null,
            (_, _) -> {
                calls.incrementAndGet();
                return Optional.of("unexpected");
            }
        );

        assertThat(provider.resolve()).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("commandがtokenを返した場合はそのtokenを返す")
    void returnsTokenFromCommand() {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicLong capturedTimeout = new AtomicLong();
        var provider = new GhAuthTokenProvider(
            7,
            () -> "/usr/bin/gh",
            (path, timeout) -> {
                capturedPath.set(path);
                capturedTimeout.set(timeout);
                return Optional.of("ghp_from_gh");
            }
        );

        assertThat(provider.resolve()).contains("ghp_from_gh");
        assertThat(capturedPath).hasValue("/usr/bin/gh");
        assertThat(capturedTimeout).hasValue(7);
    }

    @Test
    @DisplayName("stdout token抽出は空行を飛ばして最初の非空行を返す")
    void firstNonBlankLineSkipsBlankLines() {
        assertThat(GhAuthTokenProvider.firstNonBlankLine("\n  \n  ghp_token  \nignored"))
            .isEqualTo("ghp_token");
        assertThat(GhAuthTokenProvider.firstNonBlankLine(" \n\t")).isNull();
        assertThat(GhAuthTokenProvider.firstNonBlankLine(null)).isNull();
    }
}
