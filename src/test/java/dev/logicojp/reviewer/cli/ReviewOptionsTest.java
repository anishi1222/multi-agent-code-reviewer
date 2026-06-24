package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewOptions")
class ReviewOptionsTest {

    @Test
    @DisplayName("builderは未指定のgrouped optionsにデフォルトを適用する")
    void builderAppliesDefaults() {
        ReviewOptions options = ReviewOptions.builder()
            .target(new ReviewTargetSelection.Repository("owner/repo"))
            .agents(new ReviewAgentSelection.All())
            .parallelism(0)
            .build();

        assertThat(options.outputDirectory()).isEqualTo(Path.of("./reports"));
        assertThat(options.additionalAgentDirs()).isEmpty();
        assertThat(options.parallelism()).isEqualTo(1);
        assertThat(options.noSummary()).isFalse();
        assertThat(options.reviewModel()).isNull();
        assertThat(options.rubberDuck()).isFalse();
    }

    @Test
    @DisplayName("additionalAgentDirsは防御的コピーされる")
    void additionalAgentDirsAreDefensivelyCopied() {
        List<Path> dirs = new ArrayList<>(List.of(Path.of("agents")));
        ReviewOptions options = ReviewOptions.builder()
            .target(new ReviewTargetSelection.Repository("owner/repo"))
            .agents(new ReviewAgentSelection.Named(List.of("security")))
            .additionalAgentDirs(dirs)
            .build();

        dirs.add(Path.of("other"));

        assertThat(options.additionalAgentDirs()).containsExactly(Path.of("agents"));
        assertThatThrownBy(() -> options.additionalAgentDirs().add(Path.of("x")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("targetとagentsは必須")
    void targetAndAgentsAreRequired() {
        assertThatNullPointerException()
            .isThrownBy(() -> ReviewOptions.builder()
                .agents(new ReviewAgentSelection.All())
                .build());
        assertThatNullPointerException()
            .isThrownBy(() -> ReviewOptions.builder()
                .target(new ReviewTargetSelection.Repository("owner/repo"))
                .build());
    }
}
