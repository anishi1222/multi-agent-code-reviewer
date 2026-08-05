package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigDefaults")
class ConfigDefaultsTest {

    @Nested
    @DisplayName("defaultIfBlank")
    class DefaultIfBlank {

        @Test
        @DisplayName("nullの場合はデフォルト値を返す")
        void returnsDefaultForNull() {
            assertThat(ConfigDefaults.defaultIfBlank(null, "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("空文字列の場合はデフォルト値を返す")
        void returnsDefaultForEmpty() {
            assertThat(ConfigDefaults.defaultIfBlank("", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("空白のみの場合はデフォルト値を返す")
        void returnsDefaultForBlank() {
            assertThat(ConfigDefaults.defaultIfBlank("   ", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("値がある場合はそのまま返す")
        void returnsValueWhenPresent() {
            assertThat(ConfigDefaults.defaultIfBlank("value", "default")).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("defaultIfNonPositive - int")
    class DefaultIfNonPositiveInt {

        @Test
        @DisplayName("0の場合はデフォルト値を返す")
        void returnsDefaultForZero() {
            assertThat(ConfigDefaults.defaultIfNonPositive(0, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("負数の場合はデフォルト値を返す")
        void returnsDefaultForNegative() {
            assertThat(ConfigDefaults.defaultIfNonPositive(-5, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("正数の場合はそのまま返す")
        void returnsValueWhenPositive() {
            assertThat(ConfigDefaults.defaultIfNonPositive(7, 10)).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("defaultIfNonPositive - long")
    class DefaultIfNonPositiveLong {

        @Test
        @DisplayName("0の場合はデフォルト値を返す")
        void returnsDefaultForZero() {
            assertThat(ConfigDefaults.defaultIfNonPositive(0L, 10L)).isEqualTo(10L);
        }

        @Test
        @DisplayName("負数の場合はデフォルト値を返す")
        void returnsDefaultForNegative() {
            assertThat(ConfigDefaults.defaultIfNonPositive(-5L, 10L)).isEqualTo(10L);
        }

        @Test
        @DisplayName("正数の場合はそのまま返す")
        void returnsValueWhenPositive() {
            assertThat(ConfigDefaults.defaultIfNonPositive(7L, 10L)).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("defaultListIfEmpty")
    class DefaultListIfEmpty {

        private static final List<String> DEFAULT_LIST = List.of("a", "b");

        @Test
        @DisplayName("nullの場合はデフォルトリストを返す")
        void returnsDefaultForNull() {
            assertThat(ConfigDefaults.defaultListIfEmpty(null, DEFAULT_LIST)).isEqualTo(DEFAULT_LIST);
        }

        @Test
        @DisplayName("空リストの場合はデフォルトリストを返す")
        void returnsDefaultForEmpty() {
            assertThat(ConfigDefaults.defaultListIfEmpty(List.of(), DEFAULT_LIST)).isEqualTo(DEFAULT_LIST);
        }

        @Test
        @DisplayName("要素がある場合はそのまま返す")
        void returnsValueWhenNotEmpty() {
            List<String> values = List.of("x", "y");
            assertThat(ConfigDefaults.defaultListIfEmpty(values, DEFAULT_LIST)).isEqualTo(values);
        }

        @Test
        @DisplayName("返却されるリストは不変である")
        void returnedListIsImmutable() {
            List<String> result = ConfigDefaults.defaultListIfEmpty(List.of("x"), DEFAULT_LIST);
            assertThat(result).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("loadListFromResource")
    class LoadListFromResource {

        private static final List<String> FALLBACK = List.of("fallback1", "fallback2");

        @Test
        @DisplayName("存在するリソースファイルからリストを読み込む")
        void loadsFromExistingResource() {
            List<String> result = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK);
            assertThat(result).isNotEmpty();
            assertThat(result).doesNotContainNull();
        }

        @Test
        @DisplayName("存在しないリソースファイルの場合はフォールバックを返す")
        void returnsFallbackForMissingResource() {
            List<String> result = ConfigDefaults.loadListFromResource(
                "nonexistent/resource.txt", FALLBACK);
            assertThat(result).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("コメント行と空行はスキップされる")
        void skipsCommentsAndBlankLines() {
            List<String> result = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK);
            for (String item : result) {
                assertThat(item).doesNotStartWith("#");
                assertThat(item).isNotBlank();
            }
        }

        @Test
        @DisplayName("返却されるリストは不変である")
        void returnedListIsImmutable() {
            List<String> result = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK);
            assertThat(result).isUnmodifiable();
        }
    }
}
