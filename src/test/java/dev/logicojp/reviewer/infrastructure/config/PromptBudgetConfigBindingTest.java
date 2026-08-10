package dev.logicojp.reviewer.infrastructure.config;

import dev.logicojp.reviewer.shared.PromptBudget;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.bind.annotation.Bindable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Negative control for F2 (t27): `reviewer.prompt-budget` defaults must have exactly
/// one owner, [PromptBudget].
///
/// A test that merely asserts "the defaults are 12000, 6000, ..." is vacuous here,
/// because those numbers are correct both when the duplication exists and when it does
/// not. Every test below therefore either **compares the two sources through the real
/// Micronaut binding path** or **asserts the absence of a second source**, so that
/// reintroducing a competing literal fails the build.
///
/// Two competing sources previously existed and both are guarded:
///   1. `@Bindable(defaultValue = "...")` on the record components → [#recordDeclaresNoBindableDefaults()]
///   2. literal values under `reviewer.prompt-budget` in `application.yml` → [#applicationYamlDeclaresNoPromptBudgetValues()]
///
/// Source 2 silently dominated source 1, so removing only the annotations would have
/// left the drift in place; see the t27 artifact for the mutant evidence.
@DisplayName("PromptBudgetConfig default binding (F2 negative control)")
class PromptBudgetConfigBindingTest {

    private static final String PREFIX = "reviewer.prompt-budget.";

    private static final List<String> BUDGET_KEYS = List.of(
        "compact-prompts",
        "peer-content-max-chars",
        "synthesis-turn-max-chars",
        "synthesis-history-max-chars",
        "local-source-max-chars",
        "summary-content-per-agent-max-chars",
        "summary-total-max-chars",
        "summary-fallback-max-chars"
    );

    @Test
    @DisplayName("keys absent → bound values come from PromptBudget, not a second literal")
    void absentKeysFallThroughToPromptBudgetDefaults() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI)) {
            PromptBudgetConfig bound = ctx.getBean(PromptBudgetConfig.class);

            // Compares two independently-declared sources: whatever the binder produced
            // for an unset key, versus the constants PromptBudget owns. If any default
            // is ever restated elsewhere with a different value, this diverges.
            assertThat(bound.toPromptBudget())
                .as("binder output for unset keys must equal PromptBudget's own defaults")
                .isEqualTo(new PromptBudget());
        }
    }

    @Test
    @DisplayName("record components declare no @Bindable default (source 1 stays removed)")
    void recordDeclaresNoBindableDefaults() {
        RecordComponent[] components = PromptBudgetConfig.class.getRecordComponents();
        Class<?>[] paramTypes = Stream.of(components)
            .map(RecordComponent::getType)
            .toArray(Class<?>[]::new);

        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
            // @Bindable is not retained on the record component itself, so check the
            // accessor and the canonical constructor parameter too. Missing any of these
            // would make this control silently vacuous.
            List<Bindable> found = new ArrayList<>();
            found.add(components[i].getAnnotation(Bindable.class));
            found.add(components[i].getAccessor().getAnnotation(Bindable.class));
            try {
                found.add(PromptBudgetConfig.class.getDeclaredConstructor(paramTypes)
                    .getParameters()[i].getAnnotation(Bindable.class));
            } catch (NoSuchMethodException e) {
                throw new AssertionError("canonical constructor not found", e);
            }

            boolean declaresDefault = found.stream()
                .anyMatch(b -> b != null && !b.defaultValue().isEmpty());
            if (declaresDefault) {
                offenders.add(components[i].getName());
            }
        }

        assertThat(offenders)
            .as("@Bindable defaultValue re-declares a default PromptBudget already owns")
            .isEmpty();
    }

    @Test
    @DisplayName("application.yml declares no prompt-budget value (source 2 stays removed)")
    void applicationYamlDeclaresNoPromptBudgetValues() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String line : readApplicationYaml()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (String key : BUDGET_KEYS) {
                if (trimmed.startsWith(key + ":")) {
                    offenders.add(trimmed);
                }
            }
        }

        assertThat(offenders)
            .as("application.yml must not restate a default PromptBudget already owns; "
                + "it silently overrides the constants at runtime")
            .isEmpty();
    }

    @Test
    @DisplayName("explicit configuration still overrides the default")
    void explicitConfigurationStillBinds() {
        Map<String, Object> overrides = Map.of(
            PREFIX + "compact-prompts", true,
            PREFIX + "peer-content-max-chars", 111,
            PREFIX + "summary-fallback-max-chars", 222
        );

        try (ApplicationContext ctx = ApplicationContext.run(overrides, Environment.CLI)) {
            PromptBudget budget = ctx.getBean(PromptBudgetConfig.class).toPromptBudget();

            assertThat(budget.compactPrompts()).isTrue();
            assertThat(budget.peerContentMaxChars()).isEqualTo(111);
            assertThat(budget.summaryFallbackMaxChars()).isEqualTo(222);
            // Unset neighbours must still fall through rather than fail to bind.
            assertThat(budget.synthesisTurnMaxChars())
                .isEqualTo(PromptBudget.DEFAULT_SYNTHESIS_TURN_MAX_CHARS);
        }
    }

    @Test
    @DisplayName("explicit non-positive value is normalised by PromptBudget, as before")
    void nonPositiveOverrideIsNormalised() {
        try (ApplicationContext ctx = ApplicationContext.run(
                Map.of(PREFIX + "peer-content-max-chars", 0), Environment.CLI)) {

            assertThat(ctx.getBean(PromptBudgetConfig.class).toPromptBudget().peerContentMaxChars())
                .isEqualTo(PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS);
        }
    }

    private static List<String> readApplicationYaml() throws IOException {
        ClassLoader loader = PromptBudgetConfigBindingTest.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream("application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }
}
