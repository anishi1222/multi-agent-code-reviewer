package dev.logicojp.reviewer.presentation.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import dev.logicojp.reviewer.presentation.CliValidationException;
import dev.logicojp.reviewer.presentation.SkillOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillOptionsParser")
class SkillOptionsParserTest {

    private final SkillOptionsParser parser = new SkillOptionsParser();

    @Test
    @DisplayName("--help指定時はemptyを返す")
    void returnsEmptyWhenHelpRequested() {
        Optional<SkillOptions> parsed = parser.parse(new String[]{"--help"});

        assertThat(parsed).isEmpty();
    }

    @Test
    @DisplayName("skill id とオプションを正しく解釈する")
    void parsesSkillIdAndOptions() {
        Optional<SkillOptions> parsed = parser.parse(new String[]{
            "run-security-check",
            "--param", "env=prod,region=jp",
            "--model", "gpt-5",
            "--agents-dir", "agents",
            "--list"
        });

        assertThat(parsed).isPresent();
        SkillOptions options = parsed.orElseThrow();
        assertThat(options.skillId()).isEqualTo("run-security-check");
        assertThat(options.paramStrings()).containsExactly("env=prod", "region=jp");
        assertThat(options.githubToken()).isNull();
        assertThat(options.model()).isEqualTo("gpt-5");
        assertThat(options.additionalAgentDirs()).containsExactly(Path.of("agents"));
        assertThat(options.listSkills()).isTrue();
    }

    @Test
    @DisplayName("直接トークン指定はセキュリティエラー")
    void throwsWhenDirectTokenIsPassed() {
        assertThatThrownBy(() -> parser.parse(new String[]{"skill", "--token", "ghp_xxx"}))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Direct token passing");
    }

    @Test
    @DisplayName("未知オプションはエラー")
    void throwsForUnknownOption() {
        assertThatThrownBy(() -> parser.parse(new String[]{"--unknown"}))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Unknown option");
    }

    @Test
    @DisplayName("skill id が複数あるとエラー")
    void throwsWhenMultipleSkillIdsProvided() {
        assertThatThrownBy(() -> parser.parse(new String[]{"skill-one", "skill-two"}))
            .isInstanceOf(CliValidationException.class)
            .hasMessageContaining("Unexpected argument");
    }
}
