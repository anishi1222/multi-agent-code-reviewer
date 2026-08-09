package dev.logicojp.reviewer.infrastructure.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemStartupEnvironment")
class SystemStartupEnvironmentTest {

    @Test
    @DisplayName("ログディレクトリを作成しPOSIX環境では所有者限定にする")
    void createsAndHardensLogDirectory(@TempDir Path tempDirectory) throws Exception {
        Path logs = tempDirectory.resolve("logs");

        new SystemStartupEnvironment(logs, List::of).prepare();

        assertThat(logs).isDirectory();
        if (Files.getFileAttributeView(logs, PosixFileAttributeView.class) != null) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(logs)))
                .isEqualTo("rwx------");
        }
    }

    @Test
    @DisplayName("危険なJVMフラグを検出する")
    void detectsInsecureJvmFlags() {
        assertThat(SystemStartupEnvironment.detectInsecureJvmFlags(List.of(
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:OnOutOfMemoryError=/tmp/hook.sh")))
            .containsExactly("HeapDumpOnOutOfMemoryError", "OnOutOfMemoryError");
    }

    @Test
    @DisplayName("明示的に無効化されたヒープダンプフラグは検出しない")
    void ignoresExplicitlyDisabledHeapDumpFlag() {
        assertThat(SystemStartupEnvironment.detectInsecureJvmFlags(
            List.of("-XX:-HeapDumpOnOutOfMemoryError")))
            .doesNotContain("HeapDumpOnOutOfMemoryError");
    }
}
