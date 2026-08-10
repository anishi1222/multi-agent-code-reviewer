package dev.logicojp.reviewer.domain.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Fails when an [AgentConfig] element exists without a stated validation decision.
///
/// ## Why this is reflective rather than a checklist
///
/// ADR-0007 D3 requires a verdict for every element. A hand-maintained list satisfies that
/// on the day it is written and then rots: someone adds a component to the record, no test
/// mentions it, and the element becomes an unreviewed input path. `language` is the worked
/// example — it looked inert, had no rule, and reached a template lookup (SEC-L2).
///
/// Enumerating [Class#getRecordComponents()] means the coverage map cannot fall behind the
/// type. Adding a component without a decision below fails this test by construction.
///
/// The element count is **derived, never hard-coded**. ADR-0007 records the current 14-element
/// schema, while this test follows future changes reflectively instead of encoding that number.
@DisplayName("AgentConfig schema coverage (ADR-0007 D3)")
class AgentSchemaCoverageTest {

    /// Each element maps to how it is constrained. `TYPE_SATISFIED` is a real decision — it
    /// records that the Java type admits no invalid value — not an omission.
    private enum Coverage {
        /// A policy rule in AgentDefinitionPolicy rejects out-of-contract values.
        POLICY_RULE,
        /// The declared type cannot express an unsafe value (booleans, enums, records with
        /// their own validation).
        TYPE_SATISFIED,
        /// Supplied by infrastructure, never read from the definition file, so the agent
        /// author cannot influence it.
        INFRASTRUCTURE_INJECTED
    }

    private static final Map<String, Coverage> DECLARED_COVERAGE = Map.ofEntries(
        Map.entry("name", Coverage.POLICY_RULE),
        Map.entry("displayName", Coverage.POLICY_RULE),
        Map.entry("model", Coverage.POLICY_RULE),
        Map.entry("peerModel", Coverage.POLICY_RULE),
        Map.entry("systemPrompt", Coverage.POLICY_RULE),
        Map.entry("instruction", Coverage.POLICY_RULE),
        Map.entry("outputFormat", Coverage.POLICY_RULE),
        Map.entry("focusAreas", Coverage.POLICY_RULE),
        Map.entry("dialogueRounds", Coverage.POLICY_RULE),
        Map.entry("language", Coverage.POLICY_RULE),
        Map.entry("skills", Coverage.TYPE_SATISFIED),
        Map.entry("rubberDuckEnabled", Coverage.TYPE_SATISFIED),
        Map.entry("source", Coverage.TYPE_SATISFIED),
        Map.entry("skillBudget", Coverage.INFRASTRUCTURE_INJECTED)
    );

    @Test
    @DisplayName("every AgentConfig element has a stated validation decision")
    void everyElementHasADecision() {
        Set<String> actual = Arrays.stream(AgentConfig.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        assertThat(DECLARED_COVERAGE.keySet())
            .as("AgentConfig has %d elements; the coverage map must name exactly those. "
                + "An element added to the record without a decision here is an unreviewed "
                + "input path — see SEC-L2 (language) for what that costs.", actual.size())
            .containsExactlyInAnyOrderElementsOf(actual);
    }

    /// A coverage map where everything is `TYPE_SATISFIED` would pass the test above while
    /// enforcing nothing. This asserts the map still carries real rules.
    @Test
    @DisplayName("the coverage map is not vacuous")
    void coverageMapIsNotVacuous() {
        long policyRules = DECLARED_COVERAGE.values().stream()
            .filter(c -> c == Coverage.POLICY_RULE)
            .count();

        assertThat(policyRules)
            .as("most elements carry an actual rejection rule, not merely a type argument")
            .isGreaterThanOrEqualTo(10);
    }

    /// The elements marked `POLICY_RULE` must actually be rejectable. This walks the ones
    /// with a scalar contract and confirms a violating value is refused, so the map cannot
    /// claim a rule that does not exist.
    @Test
    @DisplayName("elements marked POLICY_RULE reject at least one violating value")
    void policyRuleElementsAreEnforced() {
        assertThat(reject(b -> b.name("Invalid Name With Spaces")))
            .as("name").isFalse();
        assertThat(reject(b -> b.displayName("x".repeat(AgentDefinitionPolicy.MAX_DISPLAY_NAME_LENGTH + 1))))
            .as("displayName").isFalse();
        assertThat(reject(b -> b.model("evil-vendor/backdoor")))
            .as("model").isFalse();
        assertThat(reject(b -> b.peerModel("evil-vendor/backdoor")))
            .as("peerModel").isFalse();
        assertThat(reject(b -> b.dialogueRounds(AgentDefinitionPolicy.MAX_DIALOGUE_ROUNDS + 1)))
            .as("dialogueRounds").isFalse();
        assertThat(reject(b -> b.language("de")))
            .as("language").isFalse();
        assertThat(reject(b -> b.focusAreas(java.util.stream.IntStream
                .range(0, AgentDefinitionPolicy.MAX_FOCUS_AREAS + 1)
                .mapToObj(i -> "area-" + i).toList())))
            .as("focusAreas").isFalse();
        assertThat(reject(b -> b.instruction("x".repeat(
                AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE.maxInstructionChars() + 1))))
            .as("instruction").isFalse();
        assertThat(reject(b -> b.systemPrompt("x".repeat(
                AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE.maxInstructionChars() + 1))))
            .as("systemPrompt").isFalse();
        assertThat(reject(b -> b.outputFormat("x".repeat(
                AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE.maxInstructionChars() + 1))))
            .as("outputFormat").isFalse();
    }

    private static boolean reject(java.util.function.UnaryOperator<AgentConfig.Builder> mutation) {
        AgentConfig.Builder builder = AgentConfig.builder()
            .name("fixture-agent")
            .model("claude-sonnet-4")
            .systemPrompt("Fixture prompt.")
            .instruction("Fixture instruction.")
            .outputFormat("markdown")
            .source(AgentSource.REPOSITORY_SUPPLIED);
        return AgentDefinitionPolicy.validateParsed(mutation.apply(builder).build()).accepted();
    }
}
