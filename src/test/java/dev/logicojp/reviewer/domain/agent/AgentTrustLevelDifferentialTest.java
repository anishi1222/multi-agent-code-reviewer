package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.infrastructure.parsing.AgentMarkdownParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The differential control for ADR-0007 D1/D2/D3: proves that agent-definition provenance
/// both **survives** the load path and **changes the verdict** at the end of it.
///
/// ## Why a differential and not two independent tests
///
/// Provenance that is carried but never acted on is indistinguishable from provenance that
/// was never carried — both produce a passing suite. SEC-H1 survived review for exactly that
/// reason: the strict limits existed as constants, so a reader could see "we have untrusted
/// limits", but nothing referenced them, so the observable behaviour was one uniform limit.
///
/// These tests therefore feed **byte-identical content** through the same entry point twice,
/// varying only [AgentSource], and assert the two verdicts **differ**. That assertion cannot
/// pass unless provenance reaches the leaf validator *and* the leaf validator branches on it.
/// A refactor that drops the parameter, defaults it, or applies one limit to both profiles
/// fails here, which is what makes this the negative control ADR-0007 D7 demands.
@DisplayName("Agent trust-level differential (ADR-0007 D1/D2/D3)")
class AgentTrustLevelDifferentialTest {

    private final AgentMarkdownParser parser = new AgentMarkdownParser();

    /// Sized to sit between the two instruction limits: comfortably above the strict
    /// repository-supplied ceiling (8 KiB) and comfortably below the permissive
    /// user-supplied one (32 KiB). Deriving it from the constants rather than hard-coding
    /// a number keeps the test honest if the limits are ever retuned.
    private static String definitionWithInstructionChars(int instructionChars) {
        return """
            ---
            name: differential-agent
            description: trust level differential fixture
            model: claude-sonnet-4
            ---
            ## Role
            Fixture agent.

            ## Instruction
            %s
            """.formatted("a".repeat(instructionChars));
    }

    @Test
    @DisplayName("同一定義でも user-supplied は受理し repository-supplied は拒否する")
    void sameDefinitionIsAcceptedAsUserSuppliedAndRejectedAsRepositorySupplied() {
        int between = AgentTrustProfile.forSource(AgentSource.REPOSITORY_SUPPLIED).maxInstructionChars() + 1;
        assertThat(between)
            .as("fixture must fall between the two limits, otherwise the differential proves nothing")
            .isLessThan(AgentTrustProfile.forSource(AgentSource.USER_SUPPLIED).maxInstructionChars());

        String content = definitionWithInstructionChars(between);

        var asUser = parser.parseContentSafe(content, "differential.agent.md", AgentSource.USER_SUPPLIED);
        var asRepository = parser.parseContentSafe(content, "differential.agent.md",
            AgentSource.REPOSITORY_SUPPLIED);

        assertThat(asUser.accepted())
            .as("operator-supplied definitions keep the permissive limit")
            .isTrue();
        assertThat(asRepository.accepted())
            .as("identical content from the reviewed repository must be refused — "
                + "if this passes, provenance is not reaching the validator")
            .isFalse();
        assertThat(asRepository.ruleId()).isEqualTo(AgentDefinitionPolicy.RULE_FIELD_SIZE);
    }

    @Test
    @DisplayName("両プロファイルの制約が同一なら差分テストは意味を失うため、非同一であることを固定する")
    void trustProfilesAreNotIdentical() {
        AgentTrustProfile user = AgentTrustProfile.forSource(AgentSource.USER_SUPPLIED);
        AgentTrustProfile repository = AgentTrustProfile.forSource(AgentSource.REPOSITORY_SUPPLIED);

        assertThat(repository.maxInstructionChars()).isLessThan(user.maxInstructionChars());
        assertThat(repository.maxFileChars()).isLessThan(user.maxFileChars());
        assertThat(repository.enforcesCharset())
            .as("charset enforcement is the repository-only control")
            .isTrue();
        assertThat(user.enforcesCharset()).isFalse();
        assertThat(repository.rejectsUnknownFrontmatterKeys())
            .as("closed schema applies to the untrusted side only")
            .isTrue();
        assertThat(user.rejectsUnknownFrontmatterKeys()).isFalse();
    }

    @Test
    @DisplayName("ファイルサイズ上限も provenance で分岐する")
    void fileSizeLimitDiffersByProvenance() {
        int between = AgentTrustProfile.forSource(AgentSource.REPOSITORY_SUPPLIED).maxFileChars() + 1;
        assertThat(between).isLessThan(AgentTrustProfile.forSource(AgentSource.USER_SUPPLIED).maxFileChars());

        String content = "---\nname: big\n---\n" + "a".repeat(between);

        assertThat(AgentDefinitionPolicy.validateRawContent(content, "big.agent.md",
            AgentSource.USER_SUPPLIED).accepted()).isTrue();

        var rejected = AgentDefinitionPolicy.validateRawContent(content, "big.agent.md",
            AgentSource.REPOSITORY_SUPPLIED);
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.ruleId()).isEqualTo(AgentDefinitionPolicy.RULE_FILE_SIZE);
    }
}
