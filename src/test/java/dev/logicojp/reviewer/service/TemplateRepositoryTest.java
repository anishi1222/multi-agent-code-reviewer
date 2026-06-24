package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.TemplateConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TemplateRepository")
class TemplateRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("filesystem templateを読み込み、2回目以降はcache済みcontentを返す")
    void loadsFilesystemTemplateAndReturnsCachedContent() throws IOException {
        Files.writeString(tempDir.resolve("template.md"), "v1");
        TemplateRepository repository = new TemplateRepository(config());

        assertThat(repository.loadTemplateContent("template.md")).isEqualTo("v1");

        Files.writeString(tempDir.resolve("template.md"), "v2");
        assertThat(repository.loadTemplateContent("template.md")).isEqualTo("v1");
    }

    @Test
    @DisplayName("invalid names and missing templates are rejected explicitly")
    void rejectsInvalidOrMissingTemplates() {
        TemplateRepository repository = new TemplateRepository(config());

        assertThatThrownBy(() -> repository.loadTemplateContent("../secret.md"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid template name");
        assertThatThrownBy(() -> repository.loadTemplateContent("missing.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Template not found");
    }

    private TemplateConfig config() {
        return new TemplateConfig(tempDir.toString(), null, null, null, null, null, null, null, null);
    }
}
