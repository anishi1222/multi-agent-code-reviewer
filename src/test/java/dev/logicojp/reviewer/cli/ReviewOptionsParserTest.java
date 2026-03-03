package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.config.ExecutionConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewOptionsParser")
class ReviewOptionsParserTest {

    private static final ExecutionConfig EXECUTION_CONFIG =
        ExecutionConfig.ofFlat(7, 1, 10, 5, 5, 5, 5, 10, 2, 0, 0, 0);

    @Test
    @DisplayName("--help指定時はemptyを返す")
    void returnsEmptyWhenHelpRequested() {
        var parser = newParser();

        Optional<ReviewCommand.ParsedOptions> parsed = parser.parse(new String[]{"--help"});

        assertThat(parsed).isEmpty();
    }

    @Test
    @DisplayName("最小構成のrepo/all引数を正しく解釈する")
    void parsesRepositoryAndAllAgents() {
        var parser = newParser();

        Optional<ReviewCommand.ParsedOptions> parsed = parser.parse(
            new String[]{"--repo", "owner/repo", "--all"}
        );

        assertThat(parsed).isPresent();
        ReviewCommand.ParsedOptions options = parsed.orElseThrow();
        assertThat(options.target()).isInstanceOf(ReviewCommand.TargetSelection.Repository.class);
        assertThat(((ReviewCommand.TargetSelection.Repository) options.target()).repository())
            .isEqualTo("owner/repo");
        assertThat(options.agents()).isInstanceOf(ReviewCommand.AgentSelection.All.class);
        assertThat(options.parallelism()).isEqualTo(7);
    }

    @Test
    @DisplayName("repoとlocal同時指定はエラー")
    void throwsWhenBothRepoAndLocalSpecified() {
        var parser = newParser();

        assertThatThrownBy(() -> parser.parse(new String[]{
            "--repo", "owner/repo", "--local", Path.of(".").toString(), "--all"
        }))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Specify either --repo or --local");
    }

    @Test
    @DisplayName("agent指定が無い場合はエラー")
    void throwsWhenNoAgentSelectionProvided() {
        var parser = newParser();

        assertThatThrownBy(() -> parser.parse(new String[]{"--repo", "owner/repo"}))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Either --all or --agents must be specified");
    }

    private static ReviewOptionsParser newParser() {
        return new ReviewOptionsParser(EXECUTION_CONFIG);
    }
}
