package dev.logicojp.reviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentSectionParser")
class AgentSectionParserTest {

    @Test
    @DisplayName("認識済みセクションを抽出し、重複セクションは後勝ちにする")
    void extractsRecognizedSectionsWithLastDuplicateWinning() {
        AgentSectionParser parser = new AgentSectionParser("DEFAULT FORMAT");

        var sections = parser.extractSections("""
            # Agent

            ## Role
            old role

            ## Unknown
            ignored

            ## Role
            new role

            ## Instruction
            do it
            """);

        assertThat(sections).containsOnlyKeys("role", "instruction");
        assertThat(parser.systemPrompt(sections, "body")).isEqualTo("new role");
        assertThat(parser.instruction(sections)).isEqualTo("do it");
    }

    @Test
    @DisplayName("Output Format未指定時はデフォルトを返す")
    void returnsDefaultOutputFormatWhenMissing() {
        AgentSectionParser parser = new AgentSectionParser("DEFAULT FORMAT");

        assertThat(parser.outputFormat(parser.extractSections("## Role\nrole"))).isEqualTo("DEFAULT FORMAT");
    }

    @Test
    @DisplayName("Focus Areasのbulletを抽出し、空の場合はデフォルトを返す")
    void parsesFocusAreasAndDefaultsWhenEmpty() {
        AgentSectionParser parser = new AgentSectionParser(null);

        assertThat(parser.focusAreas(parser.extractSections("""
            ## Focus Areas
            - Security
            * Performance
            """), "body")).containsExactly("Security", "Performance");

        assertThat(parser.focusAreas(parser.extractSections("""
            ## Focus Areas
            no bullets
            """), "body")).containsExactly("一般的なコード品質");
    }

    @Test
    @DisplayName("ファイル名からagent名を導出する")
    void extractsNameFromFilename() {
        assertThat(AgentSectionParser.extractNameFromFilename("security.agent.md")).isEqualTo("security");
        assertThat(AgentSectionParser.extractNameFromFilename("reviewer.md")).isEqualTo("reviewer");
    }
}
