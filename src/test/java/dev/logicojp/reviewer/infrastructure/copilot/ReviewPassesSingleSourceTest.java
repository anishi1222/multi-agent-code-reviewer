package dev.logicojp.reviewer.infrastructure.copilot;

import dev.logicojp.reviewer.application.port.inbound.DescribeReviewPlanPort;
import dev.logicojp.reviewer.application.port.inbound.ReviewPlan;
import dev.logicojp.reviewer.application.review.DescribeReviewPlanUseCase;
import dev.logicojp.reviewer.domain.agent.AgentConfig;
import dev.logicojp.reviewer.domain.review.ReviewTarget;
import dev.logicojp.reviewer.infrastructure.config.ExecutionConfig;
import dev.logicojp.reviewer.infrastructure.config.ModelConfig;
import dev.logicojp.reviewer.infrastructure.config.PromptBudgetConfig;
import dev.logicojp.reviewer.infrastructure.config.RubberDuckConfig;
import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.formatter.ReviewOutputFormatter;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Control for t24/F3: the startup banner and the executor must agree on the review-pass count.
///
/// ## Why the obvious test is worthless here
///
/// "set the property to 3, assert the banner prints 3" passes identically against the **broken**
/// code, because the broken code also printed whatever its own key said. The bug was never that
/// the banner printed the wrong number for its input — it was that the banner's input and the
/// executor's input were **different keys**:
///
/// | key                                            | pre-t28 reader | effect                       |
/// |------------------------------------------------|----------------|------------------------------|
/// | `reviewer.execution.concurrency.review-passes`  | executor       | N passes ran, banner said 1  |
/// | `reviewer.execution.review-passes`              | banner only    | banner said N, 1 pass ran    |
///
/// Every assertion below therefore compares **two independently-derived values** — never a value
/// against a literal:
///
/// 1. what {@link DescribeReviewPlanPort} reports to the banner, versus
/// 2. what {@link ReviewOrchestratorFactory#buildConfig} — the exact call
///    {@link ReviewOrchestratorFactory#execute} makes — puts into `OrchestratorConfig`.
///
/// [#legacyBannerKeyNoLongerReachesTheBanner()] is the negative control: it sets the two keys to
/// *different* values, so pre-t28 code fails it.
@DisplayName("レビューパス数の単一情報源 (t28/F3)")
class ReviewPassesSingleSourceTest {

    /// The key the executor has always read.
    private static final String PLAN_KEY = "reviewer.execution.concurrency.review-passes";

    /// The key `ReviewOutputFormatter` used to bind with `@Value`. Nothing reads it now; the
    /// container must treat it as an unknown property with no effect.
    private static final String LEGACY_BANNER_KEY = "reviewer.execution.review-passes";

    private static final String BANNER_PREFIX = "Review passes: ";

    // ------------------------------------------------------------------------------------------
    // 1. Agreement across the whole input domain, without a container
    // ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "configured={0}")
    @ValueSource(ints = {-4, 0, 1, 2, 3, 7, 64})
    @DisplayName("設定値がどれでも、バナーと実行系は同じ実効パス数になる")
    void bannerAndExecutorAgreeForEveryConfiguredValue(int configured) {
        var executionConfig = executionConfigWith(configured);

        // (a) what the executor will actually run — the production mapping, not a copy of it
        int executorPasses = new ReviewContextFactory(
            executionConfig, new ModelConfig(), new RubberDuckConfig(), new PromptBudgetConfig())
            .buildOrchestratorConfig(null, null, null, null)
            .reviewPasses();

        // (b) what presentation is told, through the port, wired exactly as the composition root
        //     wires it in ApplicationPortFactory#describeReviewPlanPort
        ReviewPlan plan =
            new DescribeReviewPlanUseCase(executionConfig::reviewPasses).describePlan();

        assertThat(plan.reviewPasses())
            .as("the plan handed to the banner must equal the pass count the executor uses")
            .isEqualTo(executorPasses);

        // (c) and the rendered banner must not add a third opinion
        assertBannerMatches(renderBanner(plan), executorPasses);
    }

    // ------------------------------------------------------------------------------------------
    // 2. Negative control — the two keys, set to different values, through the real container
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("旧バナー用キーを設定してもバナーには影響せず、実行系の値が表示される")
    void legacyBannerKeyNoLongerReachesTheBanner() {
        // Deliberately contradictory: the executor's key says 3, the retired banner key says 7.
        // Pre-t28 the banner printed 7 while three passes ran. Post-t28 the second key is inert.
        Map<String, Object> overrides = Map.of(PLAN_KEY, 3, LEGACY_BANNER_KEY, 7);

        try (ApplicationContext ctx = ApplicationContext.run(overrides, Environment.CLI)) {
            int executorPasses = executorPassesFrom(ctx);
            ReviewPlan plan = ctx.getBean(DescribeReviewPlanPort.class).describePlan();
            String banner = renderBanner(plan);

            assertThat(plan.reviewPasses())
                .as("port and executor must resolve the same key")
                .isEqualTo(executorPasses)
                .isEqualTo(3);

            assertBannerMatches(banner, executorPasses);

            assertThat(banner)
                .as("the retired key must not be able to describe a run that will not happen")
                .doesNotContain(BANNER_PREFIX + "7");
        }
    }

    // ------------------------------------------------------------------------------------------
    // 3. Defaults — neither key present anywhere, including application.yml
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("キー未設定時もバナーと実行系は既定値で一致する")
    void absentKeysLeaveBannerAndExecutorAgreeing() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI)) {
            int executorPasses = executorPassesFrom(ctx);
            ReviewPlan plan = ctx.getBean(DescribeReviewPlanPort.class).describePlan();

            assertThat(plan.reviewPasses())
                .as("with no override, both sides must still come from the same accessor")
                .isEqualTo(executorPasses)
                // compared against the constant that owns the default, not a literal 1
                .isEqualTo(ExecutionConfig.DEFAULT_REVIEW_PASSES);

            assertThat(renderBanner(plan))
                .as("a single-pass run needs no announcement")
                .doesNotContain(BANNER_PREFIX);
        }
    }

    // ------------------------------------------------------------------------------------------
    // 4. Port direction — the inbound port must be served by the application use case
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DIコンテナがDescribeReviewPlanPortをapplication層のユースケースとして解決する")
    void describeReviewPlanPortIsServedByTheApplicationUseCase() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI)) {
            // ADR-0006 D2: an inbound port whose only implementation lives in infrastructure is a
            // layer defect. `@Factory` makes the *return type* the bean, so nothing static can see
            // a mis-binding — only the container can be asked.
            assertThat(ctx.getBean(DescribeReviewPlanPort.class))
                .isInstanceOf(DescribeReviewPlanUseCase.class);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /// The executor's own derivation, reached through the same public method
    /// {@link ReviewOrchestratorFactory#execute} calls.
    private static int executorPassesFrom(ApplicationContext ctx) {
        return ctx.getBean(ReviewOrchestratorFactory.class)
            .buildConfig(null, null, null, null)
            .reviewPasses();
    }

    private static ExecutionConfig executionConfigWith(int reviewPasses) {
        return new ExecutionConfig(
            new ExecutionConfig.ConcurrencySettings(4, reviewPasses),
            null, null, null, null, null);
    }

    /// Renders a real banner through the production formatter and returns stdout.
    private static String renderBanner(ReviewPlan plan) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var formatter = new ReviewOutputFormatter(new CliOutput(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8)));

        formatter.printBanner(
            Map.of("security", new AgentConfig(
                "security", "Security", "model", "system", "instruction", null, List.of(), List.of())),
            List.of(),
            "summary-model",
            ReviewTarget.gitHub("owner/repo"),
            Path.of("reports/owner/repo"),
            "review-model",
            plan);

        return out.toString(StandardCharsets.UTF_8);
    }

    /// Asserts the banner describes exactly `expectedPasses` — including the case where the
    /// correct behaviour is to say nothing at all.
    private static void assertBannerMatches(String banner, int expectedPasses) {
        if (expectedPasses > 1) {
            assertThat(banner)
                .as("banner must report the executor's pass count verbatim")
                .contains(BANNER_PREFIX + expectedPasses + " per agent");
        } else {
            assertThat(banner)
                .as("banner must stay silent when the executor runs a single pass")
                .doesNotContain(BANNER_PREFIX);
        }
    }
}
