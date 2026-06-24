package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewTargetSelection")
class ReviewTargetSelectionTest {

    @Test
    @DisplayName("repository/local selections carry expected values")
    void createsSelections() {
        ReviewTargetSelection repository = new ReviewTargetSelection.Repository("owner/repo");
        ReviewTargetSelection local = new ReviewTargetSelection.LocalDirectory(Path.of("/tmp/repo"));

        assertThat(((ReviewTargetSelection.Repository) repository).repository()).isEqualTo("owner/repo");
        assertThat(((ReviewTargetSelection.LocalDirectory) local).directory()).isEqualTo(Path.of("/tmp/repo"));
    }
}
