package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.TemplateConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TemplateService")
class TemplateServiceTest {

    @TempDir
    Path tempDir;

    private TemplateConfig createConfig() {
        return new TemplateConfig(
            tempDir.toString(),
            "default-output-format.md",
            "report.md",
            "local-review-content.md",
            "output-constraints.md",
            "report-link-entry.md",
            new TemplateConfig.SummaryTemplates(
                "summary-system.md",
                "summary-prompt.md",
                "executive-summary.md",
                "summary-result-entry.md",
                "summary-result-error-entry.md"
            ),
            new TemplateConfig.FallbackTemplates(
                "fallback-summary.md",
                "fallback-agent-row.md",
                "fallback-agent-success.md",
                "fallback-agent-failure.md"
            )
        );
    }

    @Nested
    @DisplayName("loadTemplateContent")
    class LoadTemplateContent {

        @Test
        @DisplayName("存在するテンプレートファイルを読み込める")
        void loadsExistingTemplate() throws IOException {
            Files.writeString(tempDir.resolve("test.md"), "Hello ${name}");
            TemplateService service = new TemplateService(createConfig());
            String content = service.loadTemplateContent("test.md");
            assertThat(content).isEqualTo("Hello ${name}");
        }

        @Test
        @DisplayName("パストラバーサルを含むテンプレート名は拒否される")
        void rejectsPathTraversal() {
            TemplateService service = new TemplateService(createConfig());
            assertThatThrownBy(() -> service.loadTemplateContent("../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid template name");
        }

        @Test
        @DisplayName("スラッシュを含むテンプレート名は拒否される")
        void rejectsSlashInName() {
            TemplateService service = new TemplateService(createConfig());
            assertThatThrownBy(() -> service.loadTemplateContent("sub/template.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid template name");
        }

        @Test
        @DisplayName("バックスラッシュを含むテンプレート名は拒否される")
        void rejectsBackslashInName() {
            TemplateService service = new TemplateService(createConfig());
            assertThatThrownBy(() -> service.loadTemplateContent("sub\\template.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid template name");
        }

        @Test
        @DisplayName("存在しないテンプレートは例外を送出する")
        void nonExistentThrows() {
            TemplateService service = new TemplateService(createConfig());
            assertThatThrownBy(() -> service.loadTemplateContent("nonexistent.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Template not found");
        }

        @Test
        @DisplayName("キャッシュ上限を超えると最古エントリがエビクションされる")
        void evictsOldestEntryWhenCacheLimitExceeded() throws IOException {
            TemplateService service = new TemplateService(createConfig());

            Files.writeString(tempDir.resolve("template-01.md"), "first-v1");
            assertThat(service.loadTemplateContent("template-01.md")).isEqualTo("first-v1");

            for (int index = 2; index <= 65; index++) {
                String templateName = "template-%02d.md".formatted(index);
                Files.writeString(tempDir.resolve(templateName), "content-%02d".formatted(index));
                service.loadTemplateContent(templateName);
            }

            service.cleanUp();
            Files.writeString(tempDir.resolve("template-01.md"), "first-v2");
            assertThat(service.loadTemplateContent("template-01.md")).isEqualTo("first-v2");
        }
    }

    @Nested
    @DisplayName("applyPlaceholders")
    class ApplyPlaceholders {

        @Test
        @DisplayName("プレースホルダーを置換する")
        void replacesPlaceholders() {
            TemplateService service = new TemplateService(createConfig());
            String result = service.applyPlaceholders(
                "Hello ${name}, welcome to ${place}!",
                Map.of("name", "Alice", "place", "Wonderland"));
            assertThat(result).isEqualTo("Hello Alice, welcome to Wonderland!");
        }

        @Test
        @DisplayName("未知のプレースホルダーはそのまま残る")
        void unknownPlaceholdersRemain() {
            TemplateService service = new TemplateService(createConfig());
            String result = service.applyPlaceholders(
                "Hello ${name}!",
                Map.of());
            assertThat(result).isEqualTo("Hello ${name}!");
        }

        @Test
        @DisplayName("nullテンプレートは空文字列を返す")
        void nullTemplateReturnsEmpty() {
            TemplateService service = new TemplateService(createConfig());
            String result = service.applyPlaceholders(null, Map.of("k", "v"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空のプレースホルダーMapではテンプレートをそのまま返す")
        void emptyPlaceholdersReturnTemplate() {
            TemplateService service = new TemplateService(createConfig());
            String result = service.applyPlaceholders("unchanged", Map.of());
            assertThat(result).isEqualTo("unchanged");
        }

        @Test
        @DisplayName("nullのプレースホルダーMapではテンプレートをそのまま返す")
        void nullPlaceholdersReturnTemplate() {
            TemplateService service = new TemplateService(createConfig());
            String result = service.applyPlaceholders("unchanged", null);
            assertThat(result).isEqualTo("unchanged");
        }
    }
}
