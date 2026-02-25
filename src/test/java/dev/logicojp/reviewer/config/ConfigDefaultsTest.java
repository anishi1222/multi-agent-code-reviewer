package dev.logicojp.reviewer.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;


@DisplayName("ConfigDefaults")
class ConfigDefaultsTest {

    @Nested
    @DisplayName("defaultIfBlank")
    class DefaultIfBlank {

        @Test
        @DisplayName("nullの場合はデフォルト値を返す")
        void returnsDefaultForNull() {
            Assertions.assertThat(ConfigDefaults.defaultIfBlank(null, "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("空文字列の場合はデフォルト値を返す")
        void returnsDefaultForEmpty() {
            Assertions.assertThat(ConfigDefaults.defaultIfBlank("", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("空白のみの場合はデフォルト値を返す")
        void returnsDefaultForBlank() {
            Assertions.assertThat(ConfigDefaults.defaultIfBlank("   ", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("値がある場合はそのまま返す")
        void returnsValueWhenPresent() {
            Assertions.assertThat(ConfigDefaults.defaultIfBlank("value", "default")).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("defaultIfNonPositive - int")
    class DefaultIfNonPositiveInt {

        @Test
        @DisplayName("0の場合はデフォルト値を返す")
        void returnsDefaultForZero() {
            Assertions.assertThat(ConfigDefaults.defaultIfNonPositive(0, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("負数の場合はデフォルト値を返す")
        void returnsDefaultForNegative() {
            Assertions.assertThat(ConfigDefaults.defaultIfNonPositive(-5, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("正数の場合はそのまま返す")
        void returnsValueWhenPositive() {
            Assertions.assertThat(ConfigDefaults.defaultIfNonPositive(7, 10)).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("defaultIfNonPositive - long")
    class DefaultIfNonPositiveLong {

        @Test
        @DisplayName("0の場合はデフォルト値を返す")
        void returnsDefaultForZero() {
            Assertions.assertThat(ConfigDefaults.defaultIfNonPositive(0L, 10L)).isEqualTo(10L);
        }

        @Test
        @DisplayName("負数の場合はデフォルト値を返す")
        void returnsDefaultForNegative() {
            Assertions.assertThat(ConfigDefaults.defaultIfNonPositive(-5L, 10L)).isEqualTo(10L);
        }

        @Test
        @DisplayName("正数の場合はそのまま返す")
        void returnsValueWhenPositive() {
            Assertions.assertThat(ConfigDefaults.defaultIfNonPositive(7L, 10L)).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("defaultIfNegative")
    class DefaultIfNegative {

        @Test
        @DisplayName("0の場合はそのまま返す")
        void returnsValueForZero() {
            Assertions.assertThat(ConfigDefaults.defaultIfNegative(0, 10)).isEqualTo(0);
        }

        @Test
        @DisplayName("負数の場合はデフォルト値を返す")
        void returnsDefaultForNegative() {
            Assertions.assertThat(ConfigDefaults.defaultIfNegative(-1, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("正数の場合はそのまま返す")
        void returnsValueWhenPositive() {
            Assertions.assertThat(ConfigDefaults.defaultIfNegative(5, 10)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("defaultListIfEmpty")
    class DefaultListIfEmpty {

        private static final List<String> DEFAULT_LIST = List.of("a", "b");

        @Test
        @DisplayName("nullの場合はデフォルトリストを返す")
        void returnsDefaultForNull() {
            Assertions.assertThat(ConfigDefaults.defaultListIfEmpty(null, DEFAULT_LIST)).isEqualTo(DEFAULT_LIST);
        }

        @Test
        @DisplayName("空リストの場合はデフォルトリストを返す")
        void returnsDefaultForEmpty() {
            Assertions.assertThat(ConfigDefaults.defaultListIfEmpty(List.of(), DEFAULT_LIST)).isEqualTo(DEFAULT_LIST);
        }

        @Test
        @DisplayName("要素がある場合はそのまま返す")
        void returnsValueWhenNotEmpty() {
            var values = List.of("x", "y");
            Assertions.assertThat(ConfigDefaults.defaultListIfEmpty(values, DEFAULT_LIST)).isEqualTo(values);
        }

        @Test
        @DisplayName("返却されるリストは不変である")
        void returnedListIsImmutable() {
            var result = ConfigDefaults.defaultListIfEmpty(List.of("x"), DEFAULT_LIST);
            Assertions.assertThat(result).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("loadListFromResource")
    class LoadListFromResource {

        private static final List<String> FALLBACK = List.of("fallback1", "fallback2");

        @Test
        @DisplayName("存在するリソースファイルからリストを読み込む")
        void loadsFromExistingResource() {
            var result = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK);

            Assertions.assertThat(result).isNotEmpty();
            Assertions.assertThat(result).doesNotContainNull();
        }

        @Test
        @DisplayName("存在しないリソースファイルの場合はフォールバックを返す")
        void returnsFallbackForMissingResource() {
            var result = ConfigDefaults.loadListFromResource(
                "nonexistent/resource.txt", FALLBACK);

            Assertions.assertThat(result).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("コメント行と空行はスキップされる")
        void skipsCommentsAndBlankLines() {
            var result = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK);

            for (String item : result) {
                Assertions.assertThat(item).doesNotStartWith("#");
                Assertions.assertThat(item).isNotBlank();
            }
        }

        @Test
        @DisplayName("返却されるリストは不変である")
        void returnedListIsImmutable() {
            var result = ConfigDefaults.loadListFromResource(
                "defaults/ignored-directories.txt", FALLBACK);

            Assertions.assertThat(result).isUnmodifiable();
        }
    }
}
