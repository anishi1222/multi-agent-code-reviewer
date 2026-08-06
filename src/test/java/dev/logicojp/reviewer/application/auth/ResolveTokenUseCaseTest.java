package dev.logicojp.reviewer.application.auth;

import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Policy-level tests for the token precedence rules.
///
/// These moved out of {@code infrastructure.auth.GitHubTokenResolverTest} in t16.1. Because the
/// mechanisms now sit behind {@link AcquireGitHubTokenPort}, the gh-CLI branch can finally be
/// driven deterministically instead of shelling out to the real binary.
@DisplayName("ResolveTokenUseCase")
class ResolveTokenUseCaseTest {

    /// Records how often each mechanism was consulted so the tests can assert on short-circuiting.
    private static final class StubTokenSource implements AcquireGitHubTokenPort {
        private final Optional<String> provided;
        private final Optional<String> ghCli;
        private final AtomicInteger ghCliCalls = new AtomicInteger();

        StubTokenSource(String provided, String ghCli) {
            this.provided = Optional.ofNullable(provided);
            this.ghCli = Optional.ofNullable(ghCli);
        }

        @Override
        public Optional<String> fromProvidedValue(String providedToken) {
            return provided;
        }

        @Override
        public Optional<String> fromGhCli() {
            ghCliCalls.incrementAndGet();
            return ghCli;
        }
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("提供されたトークンが最優先され、gh CLIは呼ばれない")
        void providedTokenWinsAndShortCircuits() {
            StubTokenSource source = new StubTokenSource("ghp_provided", "ghp_from_cli");
            ResolveTokenUseCase useCase = new ResolveTokenUseCase(source, true);

            assertThat(useCase.resolve("ghp_provided")).contains("ghp_provided");
            assertThat(source.ghCliCalls).hasValue(0);
        }

        @Test
        @DisplayName("トークン未提供かつフォールバック無効時は空を返し、gh CLIは呼ばれない")
        void disabledFallbackReturnsEmptyWithoutConsultingCli() {
            StubTokenSource source = new StubTokenSource(null, "ghp_from_cli");
            ResolveTokenUseCase useCase = new ResolveTokenUseCase(source, false);

            assertThat(useCase.resolve(null)).isEmpty();
            assertThat(source.ghCliCalls).hasValue(0);
        }

        @Test
        @DisplayName("トークン未提供かつフォールバック有効時はgh CLIの値を返す")
        void enabledFallbackReturnsCliToken() {
            StubTokenSource source = new StubTokenSource(null, "ghp_from_cli");
            ResolveTokenUseCase useCase = new ResolveTokenUseCase(source, true);

            assertThat(useCase.resolve(null)).contains("ghp_from_cli");
            assertThat(source.ghCliCalls).hasValue(1);
        }

        @Test
        @DisplayName("フォールバック有効でもgh CLIが値を返さなければ空になる")
        void enabledFallbackWithSilentCliReturnsEmpty() {
            StubTokenSource source = new StubTokenSource(null, null);
            ResolveTokenUseCase useCase = new ResolveTokenUseCase(source, true);

            assertThat(useCase.resolve(null)).isEmpty();
            assertThat(source.ghCliCalls).hasValue(1);
        }
    }

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("tokenSourceがnullの場合はNullPointerExceptionを投げる")
        void nullTokenSourceIsRejected() {
            assertThatThrownBy(() -> new ResolveTokenUseCase(null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tokenSource");
        }
    }
}
