package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.review.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewTargetInstructionResolver")
class ReviewTargetInstructionResolverTest {

    @TempDir
    Path tempDir;

    private AgentConfig agentConfig() {
        return AgentConfig.builder()
            .name("security")
            .displayName("Security")
            .instruction("Review ${repository}")
            .build();
    }

    @Test
    @DisplayName("GitHubターゲットではリモート指示を返す")
    void resolvesGithubInstruction() {
        var resolver = new ReviewTargetInstructionResolver(agentConfig());

        var resolved = resolver.resolve(ReviewTarget.gitHub("owner/repo"), null);

        assertThat(resolved.instruction()).contains("owner/repo");
        assertThat(resolved.localSourceContent()).isNull();
        assertThat(resolved.isLocal()).isFalse();
    }

    @Test
    @DisplayName("ローカルターゲットではキャッシュ済みソースを優先して使う")
    void usesCachedLocalSourceWhenAvailable() {
        var computed = new AtomicBoolean(false);
        var resolver = new ReviewTargetInstructionResolver(agentConfig(), () -> computed.set(true));

        var resolved = resolver.resolve(ReviewTarget.local(tempDir), "CACHED");

        assertThat(resolved.instruction()).contains(tempDir.getFileName().toString());
        assertThat(resolved.localSourceContent()).isEqualTo("CACHED");
        assertThat(resolved.isLocal()).isTrue();
        assertThat(computed).isFalse();
    }

    @Test
    @DisplayName("ローカルターゲットでキャッシュなしならlistenerを呼ぶ")
    void notifiesWhenLocalSourceCacheMissing() {
        var computed = new AtomicBoolean(false);
        var resolver = new ReviewTargetInstructionResolver(agentConfig(), () -> computed.set(true));

        var resolved = resolver.resolve(ReviewTarget.local(tempDir), null);

        assertThat(resolved.instruction()).contains(tempDir.getFileName().toString());
        assertThat(resolved.localSourceContent()).isNull();
        assertThat(resolved.isLocal()).isFalse();
        assertThat(computed).isTrue();
    }
}
