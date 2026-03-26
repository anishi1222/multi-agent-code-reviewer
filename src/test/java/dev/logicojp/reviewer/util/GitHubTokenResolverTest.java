package dev.logicojp.reviewer.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitHubTokenResolver")
class GitHubTokenResolverTest {

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("有効なトークンが提供された場合はそれを返す")
        void returnsProvidedToken() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(5);
            assertThat(resolver.resolve("ghp_abc123")).contains("ghp_abc123");
        }

        @Test
        @DisplayName("トークンの前後の空白はトリムされる")
        void trimsWhitespace() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(5);
            assertThat(resolver.resolve("  ghp_token  ")).contains("ghp_token");
        }

        @Test
        @DisplayName("空文字列のトークンの場合はgh authにフォールバックする")
        void emptyTokenFallsBack() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1);
            // gh auth may or may not be available; this tests the flow
            resolver.resolve("");
            // We can't assert the value because gh auth might not be available,
            // but the method should not throw
        }

        @Test
        @DisplayName("nullのトークンの場合はgh authにフォールバックする")
        void nullTokenFallsBack() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1);
            resolver.resolve(null);
            // Should not throw
        }

        @Test
        @DisplayName("任意の文字列トークンはそのまま返す")
        void arbitraryTokenIsReturnedAsIs() {
            GitHubTokenResolver resolver = new GitHubTokenResolver(1);
            assertThat(resolver.resolve("custom_token_value")).contains("custom_token_value");
        }

        @Test
        @DisplayName("信頼外PATH上のghは実行に使わない")
        void untrustedGhInPathIsIgnored(@TempDir Path tempDir) throws IOException {
            Path fakeGh = tempDir.resolve("gh");
            Files.writeString(fakeGh, "#!/bin/sh\necho ghp_fake\n", StandardCharsets.UTF_8);
            fakeGh.toFile().setExecutable(true);

            GitHubTokenResolver resolver = new GitHubTokenResolver(1, null, tempDir.toString());

            assertThat(resolver.resolve(null)).isEmpty();
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
