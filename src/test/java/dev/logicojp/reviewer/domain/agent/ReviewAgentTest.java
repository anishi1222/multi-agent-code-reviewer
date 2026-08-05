package dev.logicojp.reviewer.domain.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewAgent")
class ReviewAgentTest {

    private static AgentConfig agentConfig() {
        return new AgentConfig("test-agent", "Test Agent", "model",
            "system prompt", "instruction", null, List.of("area1"), List.of());
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("agentIdとconfigを保持する")
        void storesAgentIdentityAndConfig() {
            var config = agentConfig();
            var agent = new ReviewAgent("test-agent", config);

            assertThat(agent.agentId()).isEqualTo("test-agent");
            assertThat(agent.config()).isSameAs(config);
        }

        @Test
        @DisplayName("blankのagentIdはIllegalArgumentExceptionが発生する")
        void throwsOnBlankAgentId() {
            assertThatThrownBy(() -> new ReviewAgent(" ", agentConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId must not be blank");
        }

        @Test
        @DisplayName("nullの値でNullPointerExceptionが発生する")
        void throwsOnNullValues() {
            assertThatThrownBy(() -> new ReviewAgent(null, agentConfig()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agentId must not be null");
            assertThatThrownBy(() -> new ReviewAgent("test-agent", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config must not be null");
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("displayNameはconfigの表示名を返す")
        void displayNameReturnsConfigDisplayName() {
            var agent = new ReviewAgent("test-agent", agentConfig());

            assertThat(agent.displayName()).isEqualTo("Test Agent");
        }

        @Test
        @DisplayName("modelはconfigのモデルを返す")
        void modelReturnsConfigModel() {
            var agent = new ReviewAgent("test-agent", agentConfig());

            assertThat(agent.model()).isEqualTo("model");
        }

        @Test
        @DisplayName("toStringはagentIdとmodelを含む")
        void toStringIncludesAgentIdAndModel() {
            var agent = new ReviewAgent("test-agent", agentConfig());

            assertThat(agent.toString())
                .contains("test-agent")
                .contains("model");
        }
    }

    // removed: exercised infrastructure internal ReviewSessionMessageSender, now covered by infrastructure tests
}
