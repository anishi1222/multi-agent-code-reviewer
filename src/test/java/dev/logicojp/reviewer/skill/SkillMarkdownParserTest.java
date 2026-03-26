package dev.logicojp.reviewer.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillMarkdownParser")
class SkillMarkdownParserTest {

    private final SkillMarkdownParser parser = new SkillMarkdownParser();

    @Nested
    @DisplayName("parseContent - 正常系")
    class ParseContentSuccess {

        @Test
        @DisplayName("フロントマターとプロンプトを正しくパースする")
        void parsesFullSkillFile() {
            String content = """
                ---
                name: tech-stack-analysis
                description: プロジェクトの技術スタックを分析します
                metadata:
                  agent: best-practices
                ---
                
                以下のリポジトリの技術スタックを分析してください。
                
                **対象リポジトリ**: ${repository}
                """;

            SkillDefinition skill = parser.parseContent(content, "tech-stack-analysis");

            assertThat(skill.id()).isEqualTo("tech-stack-analysis");
            assertThat(skill.name()).isEqualTo("tech-stack-analysis");
            assertThat(skill.description()).isEqualTo("プロジェクトの技術スタックを分析します");
            assertThat(skill.prompt()).contains("技術スタックを分析してください");
            assertThat(skill.prompt()).contains("${repository}");
            assertThat(skill.parameters()).isEmpty();
            assertThat(skill.metadata()).containsEntry("agent", "best-practices");
        }

        @Test
        @DisplayName("metadataがない場合も正しくパースする")
        void parsesWithoutMetadata() {
            String content = """
                ---
                name: simple-skill
                description: No metadata
                ---
                
                Simple prompt content.
                """;

            SkillDefinition skill = parser.parseContent(content, "simple-skill");

            assertThat(skill.id()).isEqualTo("simple-skill");
            assertThat(skill.name()).isEqualTo("simple-skill");
            assertThat(skill.parameters()).isEmpty();
            assertThat(skill.metadata()).isEmpty();
            assertThat(skill.prompt()).contains("Simple prompt content");
        }

        @Test
        @DisplayName("フロントマターなしの場合、全体をプロンプトとして扱う")
        void parsesWithoutFrontmatter() {
            String content = "This is just a prompt without frontmatter.";

            SkillDefinition skill = parser.parseContent(content, "raw-skill");

            assertThat(skill.id()).isEqualTo("raw-skill");
            assertThat(skill.name()).isEqualTo("raw-skill");
            assertThat(skill.prompt()).isEqualTo("This is just a prompt without frontmatter.");
        }

        @Test
        @DisplayName("クォートで囲まれた値を正しく処理する")
        void handlesQuotedValues() {
            String content = """
                ---
                name: "quoted-skill"
                description: 'Quoted Description'
                ---
                
                Prompt.
                """;

            SkillDefinition skill = parser.parseContent(content, "quoted-skill");

            assertThat(skill.name()).isEqualTo("quoted-skill");
            assertThat(skill.description()).isEqualTo("Quoted Description");
        }
    }

    @Nested
    @DisplayName("Agent Skills仕様 - SKILL.md形式")
    class AgentSkillsSpec {

        @Test
        @DisplayName("SKILL.mdファイルをディレクトリ名からIDを取得してパースする")
        void parsesSkillMdFromDirectory(@TempDir Path tempDir) throws IOException {
            Path skillDir = tempDir.resolve("code-review");
            Files.createDirectories(skillDir);

            String content = """
                ---
                name: code-review
                description: Performs comprehensive code review for quality and best practices.
                metadata:
                  agent: code-quality
                ---
                
                # Code Review
                
                Review the code for quality issues.
                """;

            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);

            SkillDefinition skill = parser.parse(skillFile);

            assertThat(skill.id()).isEqualTo("code-review");
            assertThat(skill.name()).isEqualTo("code-review");
            assertThat(skill.description()).contains("comprehensive code review");
            assertThat(skill.prompt()).contains("# Code Review");
            assertThat(skill.metadata()).containsEntry("agent", "code-quality");
        }

        @Test
        @DisplayName("metadataブロックを正しくパースする")
        void parsesMetadataBlock() {
            String content = """
                ---
                name: my-skill
                description: A skill with metadata
                metadata:
                  agent: best-practices
                  version: "1.0"
                ---
                
                Skill instructions.
                """;

            SkillDefinition skill = parser.parseContent(content, "my-skill");

            assertThat(skill.id()).isEqualTo("my-skill");
            assertThat(skill.metadata()).containsEntry("agent", "best-practices");
            assertThat(skill.metadata()).containsEntry("version", "1.0");
        }

        @Test
        @DisplayName("metadataがない場合は空のマップを返す")
        void emptyMetadataWhenNotPresent() {
            String content = """
                ---
                name: simple
                description: No metadata
                ---
                
                Instructions.
                """;

            SkillDefinition skill = parser.parseContent(content, "simple");

            assertThat(skill.metadata()).isEmpty();
        }

        @Test
        @DisplayName("Agent Skills仕様ではIDがディレクトリ名と一致する")
        void idMatchesDirectoryName() {
            String content = """
                ---
                name: tech-stack-analysis
                description: Analyze technology stack
                ---
                
                Analyze the tech stack.
                """;

            SkillDefinition skill = parser.parseContent(content, "tech-stack-analysis");

            assertThat(skill.id()).isEqualTo("tech-stack-analysis");
        }
    }

    @Nested
    @DisplayName("parseContent - 異常系")
    class ParseContentErrors {

        @Test
        @DisplayName("プロンプトが空の場合は例外を投げる")
        void throwsWhenPromptEmpty() {
            String content = """
                ---
                name: empty-prompt
                description: Has no body
                ---
                
                """;

            assertThatThrownBy(() -> parser.parseContent(content, "empty-prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no prompt content");
        }
    }

    @Nested
    @DisplayName("parse - ファイルからの読み込み")
    class ParseFile {

        @Test
        @DisplayName("SKILL.md ファイルからスキルを正しく読み込む")
        void parsesFromSkillMdFile(@TempDir Path tempDir) throws IOException {
            Path skillDir = tempDir.resolve("my-skill");
            Files.createDirectories(skillDir);

            String content = """
                ---
                name: my-skill
                description: A test skill following Agent Skills spec
                ---
                
                # My Skill
                
                Do something useful.
                """;

            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);

            SkillDefinition skill = parser.parse(skillFile);

            assertThat(skill.id()).isEqualTo("my-skill");
            assertThat(skill.name()).isEqualTo("my-skill");
            assertThat(skill.description()).contains("Agent Skills spec");
            assertThat(skill.prompt()).contains("# My Skill");
        }

        @Test
        @DisplayName("SKILL.md以外のファイルは例外を投げる")
        void throwsForNonSkillMdFile(@TempDir Path tempDir) throws IOException {
            String content = """
                ---
                name: Legacy Skill
                description: Old format
                ---
                
                Prompt content.
                """;

            Path skillFile = tempDir.resolve("legacy.skill.md");
            Files.writeString(skillFile, content);

            assertThatThrownBy(() -> parser.parse(skillFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only SKILL.md is supported");
        }
    }

    @Nested
    @DisplayName("カスタムファイル名の設定")
    class CustomFilename {

        @Test
        @DisplayName("カスタムファイル名でパーサーを作成できる")
        void createsParserWithCustomFilename() {
            SkillMarkdownParser customParser = new SkillMarkdownParser("CUSTOM.md");
            assertThat(customParser.getSkillFilename()).isEqualTo("CUSTOM.md");
        }

        @Test
        @DisplayName("デフォルトコンストラクタはSKILL.mdをファイル名として使用する")
        void defaultConstructorUsesSkillMd() {
            SkillMarkdownParser defaultParser = new SkillMarkdownParser();
            assertThat(defaultParser.getSkillFilename()).isEqualTo("SKILL.md");
        }

        @Test
        @DisplayName("nullの場合はデフォルトファイル名を使用する")
        void nullFallsBackToDefault() {
            SkillMarkdownParser nullParser = new SkillMarkdownParser(null);
            assertThat(nullParser.getSkillFilename()).isEqualTo("SKILL.md");
        }

        @Test
        @DisplayName("空文字の場合はデフォルトファイル名を使用する")
        void blankFallsBackToDefault() {
            SkillMarkdownParser blankParser = new SkillMarkdownParser("  ");
            assertThat(blankParser.getSkillFilename()).isEqualTo("SKILL.md");
        }

        @Test
        @DisplayName("カスタムファイル名でスキルファイルを認識する")
        void recognizesCustomFilename(@TempDir Path tempDir) {
            SkillMarkdownParser customParser = new SkillMarkdownParser("CUSTOM.md");
            Path customFile = tempDir.resolve("CUSTOM.md");
            Path skillFile = tempDir.resolve("SKILL.md");

            assertThat(customParser.isSkillFile(customFile)).isTrue();
            assertThat(customParser.isSkillFile(skillFile)).isFalse();
        }

        @Test
        @DisplayName("カスタムファイル名でスキルを発見する")
        void discoversCustomFilename(@TempDir Path tempDir) throws IOException {
            SkillMarkdownParser customParser = new SkillMarkdownParser("CUSTOM.md");

            Path skillDir = tempDir.resolve("my-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("CUSTOM.md"), """
                ---
                name: my-skill
                description: Custom skill file
                ---
                
                Custom instructions.
                """);

            // SKILL.md should be ignored by this parser
            Path otherDir = tempDir.resolve("other-skill");
            Files.createDirectories(otherDir);
            Files.writeString(otherDir.resolve("SKILL.md"), """
                ---
                name: other-skill
                description: Standard skill file
                ---
                
                Standard instructions.
                """);

            List<Path> discovered = customParser.discoverSkills(tempDir);

            assertThat(discovered).hasSize(1);
            assertThat(discovered.getFirst().getFileName().toString()).isEqualTo("CUSTOM.md");
        }

        @Test
        @DisplayName("カスタムファイル名でスキルをパースする")
        void parsesCustomFilename(@TempDir Path tempDir) throws IOException {
            SkillMarkdownParser customParser = new SkillMarkdownParser("CUSTOM.md");

            Path skillDir = tempDir.resolve("my-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("CUSTOM.md"), """
                ---
                name: my-skill
                description: Custom format skill
                ---
                
                Custom instructions here.
                """);

            SkillDefinition skill = customParser.parse(skillDir.resolve("CUSTOM.md"));

            assertThat(skill.id()).isEqualTo("my-skill");
            assertThat(skill.name()).isEqualTo("my-skill");
            assertThat(skill.description()).isEqualTo("Custom format skill");
        }
    }

    @Nested
    @DisplayName("discoverSkills - スキルディレクトリの探索")
    class DiscoverSkills {

        @Test
        @DisplayName("SKILL.mdを含むディレクトリを発見する")
        void discoversSkillDirectories(@TempDir Path tempDir) throws IOException {
            // Create two valid skill directories
            Path skill1Dir = tempDir.resolve("skill-a");
            Files.createDirectories(skill1Dir);
            Files.writeString(skill1Dir.resolve("SKILL.md"), """
                ---
                name: skill-a
                description: First skill
                ---
                
                Instructions for skill A.
                """);

            Path skill2Dir = tempDir.resolve("skill-b");
            Files.createDirectories(skill2Dir);
            Files.writeString(skill2Dir.resolve("SKILL.md"), """
                ---
                name: skill-b
                description: Second skill
                ---
                
                Instructions for skill B.
                """);

            // Create a directory without SKILL.md (should be ignored)
            Path noSkillDir = tempDir.resolve("not-a-skill");
            Files.createDirectories(noSkillDir);
            Files.writeString(noSkillDir.resolve("README.md"), "Not a skill.");

            List<Path> discovered = parser.discoverSkills(tempDir);

            assertThat(discovered).hasSize(2);
            assertThat(discovered).allMatch(p -> p.getFileName().toString().equals("SKILL.md"));
        }

        @Test
        @DisplayName("存在しないディレクトリの場合は空リストを返す")
        void returnsEmptyForNonExistentDir(@TempDir Path tempDir) {
            Path noSuchDir = tempDir.resolve("nonexistent");
            List<Path> discovered = parser.discoverSkills(noSuchDir);
            assertThat(discovered).isEmpty();
        }

        @Test
        @DisplayName("nullの場合は空リストを返す")
        void returnsEmptyForNull() {
            List<Path> discovered = parser.discoverSkills(null);
            assertThat(discovered).isEmpty();
        }

        @Test
        @DisplayName("skills root外を指すシンボリックリンク配下のSKILL.mdは除外する")
        void excludesSkillFilesOutsideRootViaSymlink(@TempDir Path tempDir) throws IOException {
            Path externalSkillDir = Files.createTempDirectory(tempDir.getParent(), "external-skill-");
            Files.writeString(externalSkillDir.resolve("SKILL.md"), """
                ---
                name: external-skill
                description: External skill
                ---

                Instructions outside the trusted root.
                """);

            Path linkedDir = tempDir.resolve("linked-skill");
            try {
                Files.createSymbolicLink(linkedDir, externalSkillDir);
            } catch (UnsupportedOperationException | IOException e) {
                assumeTrue(false, "Symbolic links are not supported in this environment: " + e.getMessage());
            }

            List<Path> discovered = parser.discoverSkills(tempDir);

            assertThat(discovered).isEmpty();
        }
    }

    @Nested
    @DisplayName("isSkillFile")
    class IsSkillFile {

        @Test
        @DisplayName("SKILL.mdファイルはスキルファイルとして認識される")
        void recognizesAgentSkillsSpecFile(@TempDir Path tempDir) {
            Path skillFile = tempDir.resolve("SKILL.md");
            assertThat(parser.isSkillFile(skillFile)).isTrue();
        }

        @Test
        @DisplayName(".skill.mdファイルはスキルファイルとして認識されない")
        void doesNotRecognizeLegacySkillFile(@TempDir Path tempDir) {
            Path skillFile = tempDir.resolve("my-skill.skill.md");
            assertThat(parser.isSkillFile(skillFile)).isFalse();
        }

        @Test
        @DisplayName(".agent.mdファイルはスキルファイルとして認識されない")
        void doesNotRecognizeAgentFile(@TempDir Path tempDir) {
            Path agentFile = tempDir.resolve("my-agent.agent.md");
            assertThat(parser.isSkillFile(agentFile)).isFalse();
        }

        @Test
        @DisplayName("通常の.mdファイルはスキルファイルとして認識されない")
        void doesNotRecognizePlainMd(@TempDir Path tempDir) {
            Path mdFile = tempDir.resolve("readme.md");
            assertThat(parser.isSkillFile(mdFile)).isFalse();
        }

        @Test
        @DisplayName("nullパスはfalseを返す")
        void nullPathReturnsFalse() {
            assertThat(parser.isSkillFile(null)).isFalse();
        }
    }
}
