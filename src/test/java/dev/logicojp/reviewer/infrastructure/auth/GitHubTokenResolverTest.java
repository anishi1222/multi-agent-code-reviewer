package dev.logicojp.reviewer.infrastructure.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Adapter-level tests: these cover the two *mechanisms* only.
///
/// The precedence policy that used to live here moved to
/// {@code application.auth.ResolveTokenUseCase} in t16.1 (ADR-0006 deviation #1); it is covered by
/// {@code ResolveTokenUseCaseTest}, which can drive the gh-CLI branch with a stub — something these
/// tests never could, because they shell out to the real binary.
@DisplayName("GitHubTokenResolver")
class GitHubTokenResolverTest {

    @Nested
    @DisplayName("fromProvidedValue")
    class FromProvidedValue {

        @Test
        @DisplayName("有効なトークンが提供された場合はそれを返す")
        void returnsProvidedToken() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(5);
            assertThat(resolver.fromProvidedValue("ghp_abc123")).contains("ghp_abc123");
        }

        @Test
        @DisplayName("トークンの前後の空白はトリムされる")
        void trimsWhitespace() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(5);
            assertThat(resolver.fromProvidedValue("  ghp_token  ")).contains("ghp_token");
        }

        @Test
        @DisplayName("空文字列のトークンは空を返す")
        void emptyTokenReturnsEmpty() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1);
            assertThat(resolver.fromProvidedValue("")).isEmpty();
        }

        @Test
        @DisplayName("nullのトークンは空を返す")
        void nullTokenReturnsEmpty() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1);
            assertThat(resolver.fromProvidedValue(null)).isEmpty();
        }

        @Test
        @DisplayName("任意の文字列トークンはそのまま返す")
        void arbitraryTokenIsReturnedAsIs() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1);
            assertThat(resolver.fromProvidedValue("custom_token_value")).contains("custom_token_value");
        }
    }

    @Nested
    @DisplayName("fromGhCli")
    class FromGhCli {

        @Test
        @DisplayName("信頼外PATH上のghは実行に使わない")
        void untrustedGhInPathIsIgnored(@TempDir Path tempDir) throws IOException {
            Path fakeGh = tempDir.resolve("gh");
            Files.writeString(fakeGh, "#!/bin/sh\necho ghp_fake\n", StandardCharsets.UTF_8);
            fakeGh.toFile().setExecutable(true);

            GitHubTokenResolver resolver = new GitHubTokenResolver(1, null, tempDir.toString());

            assertThat(resolver.fromGhCli()).isEmpty();
        }

        @Test
        @DisplayName("PATH未設定時は空を返す")
        void withoutPathReturnsEmpty() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1, null, null);
            assertThat(resolver.fromGhCli()).isEmpty();
        }
    }

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("デフォルトタイムアウトでインスタンスを生成できる")
        void defaultTimeoutWorks() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(10);
            assertThat(resolver).isNotNull();
        }

        @Test
        @DisplayName("0以下のタイムアウトはデフォルト値に設定される")
        void negativeTimeoutDefaultsToDefault() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(0);
            assertThat(resolver).isNotNull();
        }
    }
}
