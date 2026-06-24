package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewAgentSelection")
class ReviewAgentSelectionTest {

    @Test
    @DisplayName("all/named selections carry expected values")
    void createsSelections() {
        ReviewAgentSelection all = new ReviewAgentSelection.All();
        ReviewAgentSelection named = new ReviewAgentSelection.Named(List.of("security", "quality"));

        assertThat(all).isInstanceOf(ReviewAgentSelection.All.class);
        assertThat(((ReviewAgentSelection.Named) named).agents())
            .containsExactly("security", "quality");
    }
}
