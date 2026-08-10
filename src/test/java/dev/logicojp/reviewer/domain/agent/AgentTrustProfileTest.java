package dev.logicojp.reviewer.domain.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Pins the trust-profile limits and, critically, the *unit* they are measured in.
///
/// ADR-0007 leaves characters-vs-bytes to the implementer. That choice is invisible in the
/// source (`content.length()` reads the same either way) but changes behaviour by roughly 3x
/// on the Japanese text these definitions are largely written in. A silent flip from
/// characters to bytes would reject definitions that are legal today, and nothing in the
/// codebase would object. These tests make the choice explicit and breakable.
@DisplayName("AgentTrustProfile (ADR-0007 D2)")
class AgentTrustProfileTest {

    @Nested
    @DisplayName("measurement unit")
    class MeasurementUnit {

        /// Every character here is 3 bytes in UTF-8. A definition sized just under the
        /// character limit is therefore far over it when counted as bytes, so this fixture
        /// fails immediately if anyone switches the unit.
        @Test
        @DisplayName("limits count UTF-16 characters, not UTF-8 bytes")
        void limitsCountCharactersNotBytes() {
            AgentTrustProfile profile = AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE;
            String japanese = "あ".repeat(profile.maxInstructionChars() - 1);

            assertThat(japanese.length())
                .as("fixture is just under the character limit")
                .isLessThan(profile.maxInstructionChars());

            assertThat(japanese.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .as("the same fixture is far over the limit when counted as bytes — "
                    + "if the implementation ever switches to bytes this content starts "
                    + "being rejected, which is the regression this test exists to catch")
                .isGreaterThan(profile.maxInstructionChars() * 2);

            AgentConfig config = definitionWithInstruction(japanese, AgentSource.REPOSITORY_SUPPLIED);
            assertThat(AgentDefinitionPolicy.validateParsed(config).accepted())
                .as("multi-byte content under the character limit must be accepted")
                .isTrue();
        }

        @Test
        @DisplayName("one character over the limit is rejected regardless of encoding width")
        void oneCharacterOverIsRejected() {
            AgentTrustProfile profile = AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE;
            String tooLong = "あ".repeat(profile.maxInstructionChars() + 1);

            AgentConfig config = definitionWithInstruction(tooLong, AgentSource.REPOSITORY_SUPPLIED);
            AgentDefinitionPolicy.PolicyResult result = AgentDefinitionPolicy.validateParsed(config);

            assertThat(result.accepted()).isFalse();
            assertThat(result.ruleId()).isEqualTo(AgentDefinitionPolicy.RULE_FIELD_SIZE);
        }
    }

    @Nested
    @DisplayName("profile selection")
    class ProfileSelection {

        @Test
        @DisplayName("every source maps to exactly one profile")
        void everySourceMapsToOneProfile() {
            assertThat(AgentTrustProfile.forSource(AgentSource.USER_SUPPLIED))
                .isEqualTo(AgentTrustProfile.USER_SUPPLIED_PROFILE);
            assertThat(AgentTrustProfile.forSource(AgentSource.REPOSITORY_SUPPLIED))
                .isEqualTo(AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE);
        }

        /// A null source must not quietly pick the permissive profile. Fail-closed here is
        /// the difference between "we could not determine provenance" and "we granted the
        /// caller operator trust".
        @Test
        @DisplayName("unknown provenance falls back to the strict profile")
        void unknownProvenanceIsStrict() {
            assertThat(AgentTrustProfile.forSource(null))
                .isEqualTo(AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE);
        }

        @Test
        @DisplayName("the repository profile is strictly tighter on every bound")
        void repositoryProfileIsStrictlyTighter() {
            AgentTrustProfile user = AgentTrustProfile.USER_SUPPLIED_PROFILE;
            AgentTrustProfile repo = AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE;

            assertThat(repo.maxFileChars()).isLessThan(user.maxFileChars());
            assertThat(repo.maxInstructionChars()).isLessThan(user.maxInstructionChars());
            assertThat(repo.enforcesCharset()).isTrue();
            assertThat(user.enforcesCharset()).isFalse();
            assertThat(repo.rejectsUnknownFrontmatterKeys()).isTrue();
            assertThat(user.rejectsUnknownFrontmatterKeys()).isFalse();
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a non-positive limit is rejected at construction")
        void nonPositiveLimitRejected() {
            assertThatThrownBy(() -> new AgentTrustProfile(0, 100, 10, true, true))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AgentTrustProfile(100, -1, 10, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static AgentConfig definitionWithInstruction(String instruction, AgentSource source) {
        return AgentConfig.builder()
            .name("fixture-agent")
            .displayName("Fixture")
            .model("claude-sonnet-4")
            .systemPrompt("Fixture prompt.")
            .instruction(instruction)
            .outputFormat("markdown")
            .source(source)
            .build();
    }
}
