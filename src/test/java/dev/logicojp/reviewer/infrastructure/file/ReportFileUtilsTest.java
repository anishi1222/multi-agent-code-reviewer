package dev.logicojp.reviewer.infrastructure.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportFileUtils")
class ReportFileUtilsTest {

    @Nested
    @DisplayName("ensureOutputDirectory")
    class EnsureOutputDirectory {

        @Test
        @DisplayName("存在しないディレクトリを作成する")
        void createsNonExistentDirectory(@TempDir Path tempDir) throws IOException {
            Path newDir = tempDir.resolve("reports/sub/dir");
            assertThat(newDir).doesNotExist();

            ReportFileUtils.ensureOutputDirectory(newDir);

            assertThat(newDir).isDirectory();
        }

        @Test
        @DisplayName("既存のディレクトリには何もしない")
        void doesNothingForExistingDirectory(@TempDir Path tempDir) throws IOException {
            assertThat(tempDir).isDirectory();

            ReportFileUtils.ensureOutputDirectory(tempDir);

            assertThat(tempDir).isDirectory();
        }

        @Test
        @DisplayName("ネストされたディレクトリを再帰的に作成する")
        void createsNestedDirectories(@TempDir Path tempDir) throws IOException {
            Path nested = tempDir.resolve("a/b/c/d");

            ReportFileUtils.ensureOutputDirectory(nested);

            assertThat(nested).isDirectory();
        }

        @Test
        @DisplayName("writeSecureStringでファイルを作成できる")
        void writesSecureStringFile(@TempDir Path tempDir) throws IOException {
            Path target = tempDir.resolve("report.md");

            ReportFileUtils.writeSecureString(target, "content");

            assertThat(target).exists();
            assertThat(Files.readString(target)).isEqualTo("content");
        }

        @Test
        @DisplayName("POSIX環境ではwriteSecureStringがowner-only権限を設定する")
        void writeSecureStringSetsOwnerOnlyPermissionsOnPosix(@TempDir Path tempDir) throws IOException {
            Path target = tempDir.resolve("secure-report.md");

            ReportFileUtils.writeSecureString(target, "secret");

            if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target);
                assertThat(permissions).containsExactlyInAnyOrder(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                );
            }
        }
    }
}
