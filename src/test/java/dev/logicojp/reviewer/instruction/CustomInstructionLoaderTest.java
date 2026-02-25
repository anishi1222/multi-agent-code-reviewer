package dev.logicojp.reviewer.instruction;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


@DisplayName("CustomInstructionLoader")
class CustomInstructionLoaderTest {

    @Nested
    @DisplayName("loadForTarget - GitHubTarget")
    class LoadForTargetGitHub {

        @Test
        @DisplayName("GitHubTargetの場合は空リストを返す")
        void returnsEmptyForGitHub() {
            var loader = new CustomInstructionLoader(null, true);
            var target = ReviewTarget.gitHub("owner/repo");

            Assertions.assertThat(loader.loadForTarget(target)).isEmpty();
        }
    }

    @Nested
    @DisplayName("loadForTarget - LocalTarget")
    class LoadForTargetLocal {

        @Test
        @DisplayName("ディレクトリが存在しない場合は空リストを返す")
        void emptyForNonExistentDir(@TempDir Path tempDir) {
            var loader = new CustomInstructionLoader(null, true);
            var target = ReviewTarget.local(tempDir.resolve("nonexistent"));

            Assertions.assertThat(loader.loadForTarget(target)).isEmpty();
        }

        @Test
        @DisplayName("空のディレクトリの場合は空リストを返す")
        void emptyForEmptyDir(@TempDir Path tempDir) {
            var loader = new CustomInstructionLoader(null, true);
            var target = ReviewTarget.local(tempDir);

            Assertions.assertThat(loader.loadForTarget(target)).isEmpty();
        }
    }

    @Nested
    @DisplayName("loadFromLocalDirectory")
    class LoadFromLocalDirectory {

        @Test
        @DisplayName("copilot-instructions.mdを読み込む")
        void loadsCopilotInstructions(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("copilot-instructions.md");
            Files.writeString(file, "Review all code carefully.", StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, true);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).hasSize(1);
            Assertions.assertThat(result.getFirst().content()).isEqualTo("Review all code carefully.");
            Assertions.assertThat(result.getFirst().source()).isEqualTo(CustomInstruction.Source.LOCAL_FILE);
        }

        @Test
        @DisplayName(".github/copilot-instructions.mdを読み込む")
        void loadsGithubCopilotInstructions(@TempDir Path tempDir) throws IOException {
            Path dir = tempDir.resolve(".github");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("copilot-instructions.md"), "GitHub instructions.",
                StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, true);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).anySatisfy(ci ->
                Assertions.assertThat(ci.content()).isEqualTo("GitHub instructions."));
        }

        @Test
        @DisplayName("空白のみのファイルは無視される")
        void ignoresBlankFiles(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("copilot-instructions.md"), "   \n  ",
                StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, true);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("nullディレクトリの場合は空リストを返す")
        void emptyForNull() {
            var loader = new CustomInstructionLoader(null, true);
            Assertions.assertThat(loader.loadFromLocalDirectory(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("スコープ付きインストラクション")
    class ScopedInstructions {

        @Test
        @DisplayName(".github/instructions/*.instructions.mdを読み込む")
        void loadsScopedInstructions(@TempDir Path tempDir) throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);

            String content = """
                ---
                applyTo: '**/*.java'
                description: 'Java guidelines'
                ---
                Follow Java conventions.
                """;
            Files.writeString(instructionsDir.resolve("java.instructions.md"), content,
                StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, true);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).anySatisfy(ci -> {
                Assertions.assertThat(ci.content()).contains("Follow Java conventions.");
                Assertions.assertThat(ci.applyTo()).isEqualTo("**/*.java");
                Assertions.assertThat(ci.description()).isEqualTo("Java guidelines");
            });
        }

        @Test
        @DisplayName("拡張子が一致しないファイルは無視される")
        void ignoresNonInstructionFiles(@TempDir Path tempDir) throws IOException {
            Path instructionsDir = tempDir.resolve(".github/instructions");
            Files.createDirectories(instructionsDir);

            Files.writeString(instructionsDir.resolve("notes.md"), "Not an instruction.",
                StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, true);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).noneMatch(ci -> ci.content().contains("Not an instruction."));
        }
    }

    @Nested
    @DisplayName("プロンプトファイル")
    class PromptFiles {

        @Test
        @DisplayName(".github/prompts/*.prompt.mdを読み込む")
        void loadsPromptFiles(@TempDir Path tempDir) throws IOException {
            Path promptsDir = tempDir.resolve(".github/prompts");
            Files.createDirectories(promptsDir);

            Files.writeString(promptsDir.resolve("review.prompt.md"), "Review prompt content.",
                StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, true);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).anySatisfy(ci ->
                Assertions.assertThat(ci.content()).isEqualTo("Review prompt content."));
        }

        @Test
        @DisplayName("loadPrompts=falseの場合はプロンプトファイルを無視する")
        void ignoresPromptsWhenDisabled(@TempDir Path tempDir) throws IOException {
            Path promptsDir = tempDir.resolve(".github/prompts");
            Files.createDirectories(promptsDir);

            Files.writeString(promptsDir.resolve("review.prompt.md"), "Review prompt content.",
                StandardCharsets.UTF_8);

            var loader = new CustomInstructionLoader(null, false);
            List<CustomInstruction> result = loader.loadFromLocalDirectory(tempDir);

            Assertions.assertThat(result).noneMatch(ci -> ci.content().contains("Review prompt content."));
        }
    }

    @Nested
    @DisplayName("parseFrontmatter")
    class ParseFrontmatter {

        @Test
        @DisplayName("フロントマターなしの場合はそのまま返す")
        void noFrontmatter() {
            var parsed = CustomInstructionLoader.parseFrontmatter("Plain content");

            Assertions.assertThat(parsed.content()).isEqualTo("Plain content");
            Assertions.assertThat(parsed.applyTo()).isNull();
            Assertions.assertThat(parsed.description()).isNull();
        }

        @Test
        @DisplayName("フロントマター付きの場合はメタデータを抽出する")
        void withFrontmatter() {
            String raw = """
                ---
                applyTo: '**/*.ts'
                description: 'TypeScript rules'
                ---
                Use strict mode.
                """;

            var parsed = CustomInstructionLoader.parseFrontmatter(raw);

            Assertions.assertThat(parsed.content()).contains("Use strict mode.");
            Assertions.assertThat(parsed.applyTo()).isEqualTo("**/*.ts");
            Assertions.assertThat(parsed.description()).isEqualTo("TypeScript rules");
        }
    }
}
