package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.auth.ResolveTokenUseCase;
import dev.logicojp.reviewer.application.port.inbound.ResolveTokenPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.application.port.outbound.AcquireGitHubTokenPort;
import dev.logicojp.reviewer.application.port.outbound.CreateReviewSessionPortsPort;
import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort;
import dev.logicojp.reviewer.application.review.ReviewOrchestrator;
import dev.logicojp.reviewer.application.skill.ExecuteSkillUseCase;
import dev.logicojp.reviewer.application.port.inbound.ExecuteSkillPort;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import dev.logicojp.reviewer.infrastructure.auth.GitHubTokenResolver;
import io.micronaut.context.env.Environment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Locks in the port-direction fixes made by t16.1 and t16.3 (ADR-0006 deviations #1, #2 and #8).
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
@DisplayName("ポート方向修正のDI配線 (t16.1/t16.3)")
class PortDirectionWiringTest {

    @Inject
    ExecuteSkillPort executeSkillPort;

    @Inject
    LoadAgentPort loadAgentPort;

    @Inject
    ResolveTokenPort resolveTokenPort;

    @Inject
    AcquireGitHubTokenPort acquireGitHubTokenPort;

    @Inject
    RunReviewPort runReviewPort;

    @Inject
    ResolveReviewSettingsPort resolveReviewSettingsPort;

    @Inject
    CreateReviewSessionPortsPort createReviewSessionPortsPort;

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
    @DisplayName("発見済みスキルの単一カタログをLoadAgentPortとExecuteSkillPortが共有する")
    void agentDiscoveryPopulatesTheCatalogUsedByExecuteSkillPort() {
        loadAgentPort.loadAll(List.of(AgentSourceDirectory.userSupplied(Path.of("agents"))));

        assertThat(executeSkillPort.listSkills())
            .as("skills parsed during agent loading must reach the executable catalog")
            .extracting(skill -> skill.id())
            .contains("java-junit");
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

    @Test
    @DisplayName("DIコンテナがRunReviewPortをapplication層のReviewOrchestratorとして解決する")
    void runReviewPortIsBackedByTheApplicationOrchestrator() {
        // Before t16.3 this resolved to infrastructure.copilot.ReviewOrchestratorFactory, which
        // implemented the inbound port and bypassed the application implementation.
        assertThat(runReviewPort)
            .as("RunReviewPort must be served by the application orchestrator, not an adapter")
            .isNotNull()
            .isInstanceOf(ReviewOrchestrator.class);
    }

    @Test
    @DisplayName("レビュー設定写像はoutboundアダプタとして解決される")
    void reviewConfigurationIsBackedByTheOutboundAdapter() {
        assertThat(resolveReviewSettingsPort)
            .isNotNull()
            .isInstanceOf(ReviewContextFactory.class);
    }

    @Test
    @DisplayName("SDKセッション構築はoutboundアダプタとして解決される")
    void reviewSessionFactoryIsBackedByTheOutboundAdapter() {
        assertThat(createReviewSessionPortsPort)
            .isNotNull()
            .isInstanceOf(ReviewOrchestratorFactory.class);
    }
}
