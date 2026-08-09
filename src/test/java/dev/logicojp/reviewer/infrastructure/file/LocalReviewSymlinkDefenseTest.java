package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.review.LocalFileSelectionConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Executable coverage for TGT-07 on the actual local-review collection path.
@DisplayName("local review symlink traversal defense")
class LocalReviewSymlinkDefenseTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("レビュー対象外を指すシンボリックリンクを拒否し通常ファイルは収集する")
    void rejectsExternalSymlinkWhileCollectingSafeFile() throws IOException {
        Path reviewRoot = Files.createDirectory(tempDir.resolve("review-root"));
        Path outsideRoot = Files.createDirectory(tempDir.resolve("outside-root"));
        Path safeFile = reviewRoot.resolve("Safe.java");
        Path outsideFile = outsideRoot.resolve("Secret.java");
        Path externalLink = reviewRoot.resolve("Leaked.java");
        Files.writeString(safeFile, "class Safe { String marker = \"safe-marker\"; }\n");
        Files.writeString(outsideFile, "class Secret { String marker = \"outside-secret-marker\"; }\n");
        Files.createSymbolicLink(externalLink, outsideFile);

        LocalFileProvider.CollectionResult result = new LocalFileProvider().collectAndFormat(
            reviewRoot,
            LocalFileSelectionConfig.of(
                1_024,
                10_000,
                List.of(),
                List.of("java"),
                List.of(),
                List.of())
        );

        assertThat(Files.isSymbolicLink(externalLink))
            .as("the malicious fixture must actually be a symlink")
            .isTrue();
        assertThat(result.fileCount()).isEqualTo(1);
        assertThat(result.reviewContent())
            .contains("Safe.java", "safe-marker")
            .doesNotContain("Leaked.java", "outside-secret-marker");
        assertThat(result.directorySummary())
            .contains("Safe.java")
            .doesNotContain("Leaked.java", "Secret.java");
    }
}
