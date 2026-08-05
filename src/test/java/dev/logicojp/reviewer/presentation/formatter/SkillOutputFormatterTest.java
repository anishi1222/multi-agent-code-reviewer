package dev.logicojp.reviewer.presentation.formatter;

import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import dev.logicojp.reviewer.presentation.CliOutput;

@DisplayName("SkillOutputFormatter")
class SkillOutputFormatterTest {

    @Test
    @DisplayName("スキル一覧を整形して表示する")
    void printsSkillList() {
        var outBuffer = new ByteArrayOutputStream();
        var formatter = new SkillOutputFormatter(
            new CliOutput(new PrintStream(outBuffer), new PrintStream(new ByteArrayOutputStream()))
        );

        SkillDefinition skill = new SkillDefinition(
            "scan",
            "Scan",
            "Run scan",
            "instr",
            List.of(SkillParameter.required("path", "Target path")),
            Map.of()
        );

        formatter.printAvailableSkills(List.of(skill));

        String text = outBuffer.toString();
        assertThat(text).contains("Available Skills");
        assertThat(text).contains("scan");
        assertThat(text).contains("Name: Scan");
        assertThat(text).contains("Description: Run scan");
        assertThat(text).contains("path: Target path (required)");
    }

    @Test
    @DisplayName("空一覧時はNo skills foundを表示する")
    void printsEmptyMessageWhenNoSkills() {
        var outBuffer = new ByteArrayOutputStream();
        var formatter = new SkillOutputFormatter(
            new CliOutput(new PrintStream(outBuffer), new PrintStream(new ByteArrayOutputStream()))
        );

        formatter.printAvailableSkills(List.of());

        assertThat(outBuffer.toString()).contains("No skills found.");
    }
}
