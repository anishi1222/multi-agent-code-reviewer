package dev.logicojp.reviewer.domain.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileSelectionConfig")
class LocalFileSelectionConfigTest {

    @Test
    @DisplayName("設定値を正規化して小文字セットへ変換する")
    void normalizesConfiguredValues() {
        LocalFileSelectionConfig normalized = LocalFileSelectionConfig.of(
            123,
            456,
            List.of(".Git", "build", "  "),
            List.of("JAVA", "Ts"),
            List.of("Secret", ".ENV"),
            List.of("PEM", "KEY")
        );

        assertThat(normalized.maxFileSize()).isEqualTo(123);
        assertThat(normalized.maxTotalSize()).isEqualTo(456);
        assertThat(normalized.ignoredDirectories()).containsExactlyInAnyOrder(".git", "build");
        assertThat(normalized.sourceExtensions()).containsExactlyInAnyOrder("java", "ts");
        assertThat(normalized.sensitiveFilePatterns()).containsExactlyInAnyOrder("secret", ".env");
        assertThat(normalized.sensitiveExtensions()).containsExactlyInAnyOrder("pem", "key");
    }

    @Test
    @DisplayName("null設定では空の正規化セットを生成する")
    void usesEmptySetsForNullLists() {
        LocalFileSelectionConfig normalized = LocalFileSelectionConfig.of(
            123,
            456,
            null,
            null,
            null,
            null
        );

        assertThat(normalized.ignoredDirectories()).isEmpty();
        assertThat(normalized.sourceExtensions()).isEmpty();
        assertThat(normalized.sensitiveFilePatterns()).isEmpty();
        assertThat(normalized.sensitiveExtensions()).isEmpty();
    }
}
