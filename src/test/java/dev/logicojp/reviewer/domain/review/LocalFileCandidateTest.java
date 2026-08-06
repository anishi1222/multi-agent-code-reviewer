package dev.logicojp.reviewer.domain.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileCandidate")
class LocalFileCandidateTest {

    @Nested
    @DisplayName("レコードフィールド")
    class RecordFields {

        @Test
        @DisplayName("pathとsizeにアクセスできる")
        void accessFields() {
            Path path = Path.of("src/main/java/App.java");
            var candidate = new LocalFileCandidate(path, 1024);

            assertThat(candidate.path()).isEqualTo(path);
            assertThat(candidate.size()).isEqualTo(1024);
        }

        @Test
        @DisplayName("サイズ0のファイルを表現できる")
        void zeroSizeFile() {
            var candidate = new LocalFileCandidate(Path.of("empty.txt"), 0);

            assertThat(candidate.size()).isZero();
        }
    }

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("同じpathとsizeのインスタンスは等しい")
        void equalInstances() {
            Path path = Path.of("file.java");
            var a = new LocalFileCandidate(path, 100);
            var b = new LocalFileCandidate(path, 100);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("異なるsizeのインスタンスは等しくない")
        void differentSizes() {
            Path path = Path.of("file.java");
            var a = new LocalFileCandidate(path, 100);
            var b = new LocalFileCandidate(path, 200);

            assertThat(a).isNotEqualTo(b);
        }
    }
}
