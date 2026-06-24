package dev.logicojp.reviewer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RubberDuckConfig")
class RubberDuckConfigTest {

    @Test
    @DisplayName("dialogueRoundsは範囲内に正規化される")
    void normalizesDialogueRounds() {
        assertThat(new RubberDuckConfig(true, 0, null, null).dialogueRounds())
            .isEqualTo(RubberDuckConfig.DEFAULT_DIALOGUE_ROUNDS);
        assertThat(new RubberDuckConfig(true, 99, null, null).dialogueRounds())
            .isEqualTo(RubberDuckConfig.MAX_DIALOGUE_ROUNDS);
    }

    @Test
    @DisplayName("blank peerModelとstrategyは既定値に正規化される")
    void normalizesBlankPeerModelAndStrategy() {
        RubberDuckConfig config = new RubberDuckConfig(true, 2, "  ", "  ");

        assertThat(config.peerModel()).isNull();
        assertThat(config.synthesisStrategy()).isEqualTo(RubberDuckConfig.DEFAULT_SYNTHESIS_STRATEGY);
        assertThat(config.isDedicatedSessionSynthesis()).isFalse();
    }

    @Test
    @DisplayName("dedicated-session strategyを判定できる")
    void detectsDedicatedSessionStrategy() {
        RubberDuckConfig config = new RubberDuckConfig(
            true,
            2,
            "peer",
            RubberDuckConfig.SYNTHESIS_DEDICATED_SESSION
        );

        assertThat(config.isDedicatedSessionSynthesis()).isTrue();
    }
}
