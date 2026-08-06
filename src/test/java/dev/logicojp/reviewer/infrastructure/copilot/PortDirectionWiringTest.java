package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.auth.ResolveTokenUseCase;
import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;
import dev.logicojp.reviewer.application.skill.ExecuteSkillUseCase;
import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.infrastructure.auth.GitHubTokenResolver;
import io.micronaut.context.env.Environment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Locks in the two port-direction fixes made by t16.1 (ADR-0006 deviations #1 and #2).
///
/// `LayerDependencyRulesTest` Rule 4 already fails if an infrastructure class *references*
/// `application.port.inbound`, but a static rule cannot show which bean the container actually
/// hands out. Both defects were of exactly that shape — the correct use case existed and was
/// simply not the thing wired — so the runtime binding is asserted here, in the same style as
/// {@link LoadAgentPortWiringTest}.
///
/// If someone re-points either factory method at an infrastructure implementation, Rule 4 catches
/// the layering violation and these tests catch the behavioural regression.
@MicronautTest(environments = Environment.CLI)
@DisplayName("ポート方向修正のDI配線 (t16.1)")
class PortDirectionWiringTest {

    @Inject
    ExecuteSkillPort executeSkillPort;

    @Inject
    ResolveTokenPort resolveTokenPort;

    @Inject
    AcquireGitHubTokenPort acquireGitHubTokenPort;

    @Test
    @DisplayName("DIコンテナがExecuteSkillPortをExecuteSkillUseCaseとして解決する")
    void executeSkillPortIsBackedByTheUseCase() {
        // Before t16.1 this resolved to infrastructure.copilot.SkillExecutor, leaving
        // ExecuteSkillUseCase as unreachable dead code.
        assertThat(executeSkillPort)
            .as("ExecuteSkillPort must be served by the application use case, not an adapter")
            .isNotNull()
            .isInstanceOf(ExecuteSkillUseCase.class);
    }

    @Test
    @DisplayName("配線されたExecuteSkillPortが登録済みスキルを列挙できる")
    void executeSkillPortListsRegisteredSkills() {
        // Exercises the injected registry lambdas, which is the part the swap actually rewired.
        assertThat(executeSkillPort.listSkills())
            .as("the getAll supplier must be bound to the real SkillRegistry")
            .isNotNull();
    }

    @Test
    @DisplayName("DIコンテナがResolveTokenPortをResolveTokenUseCaseとして解決する")
    void resolveTokenPortIsBackedByTheUseCase() {
        // Before t16.1 the inbound port was implemented directly by GitHubTokenResolver.
        assertThat(resolveTokenPort)
            .as("ResolveTokenPort must be served by the application use case, not the adapter")
            .isNotNull()
            .isInstanceOf(ResolveTokenUseCase.class);
    }

    @Test
    @DisplayName("GitHubTokenResolverはoutboundポートとしてのみ解決される")
    void gitHubTokenResolverIsBoundOnlyToTheOutboundPort() {
        assertThat(acquireGitHubTokenPort)
            .as("the adapter must still be reachable, but as the outbound mechanism port")
            .isNotNull()
            .isInstanceOf(GitHubTokenResolver.class);

        assertThat(resolveTokenPort)
            .as("the adapter must no longer satisfy the inbound port")
            .isNotInstanceOf(GitHubTokenResolver.class);
    }

    @Test
    @DisplayName("配線されたResolveTokenPortが提供トークンを最優先で返す")
    void resolveTokenPortAppliesPrecedenceThroughTheWiredGraph() {
        // End-to-end through the real bean graph: use case policy -> adapter mechanism.
        assertThat(resolveTokenPort.resolve("  ghp_wired  ")).contains("ghp_wired");
    }
}
