package dev.logicojp.reviewer.shared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/// Default-value helpers for configuration records.
///
/// Moved from {@code config.ConfigDefaults} to the shared layer so that
/// domain types can reference it without importing an infrastructure-adjacent
/// package. Only {@code java.*} dependencies are used here.
///
/// The SLF4J logging calls present in the original were removed:
/// resource-loading fallbacks now proceed silently, which is appropriate
/// for a utility that is called at configuration-binding time.
public final class ConfigDefaults {

    private ConfigDefaults() {
    }

    /// Returns {@code defaultValue} when {@code value} is null or blank.
    public static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /// Returns {@code defaultValue} when {@code value} is zero or negative.
    public static int defaultIfNonPositive(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    /// Returns {@code defaultValue} when {@code value} is zero or negative.
    public static long defaultIfNonPositive(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    /// Returns {@code defaultValue} when {@code value} is zero or negative.
    public static double defaultIfNonPositive(double value, double defaultValue) {
        return value <= 0.0 ? defaultValue : value;
    }

    /// Returns {@code defaultValue} when {@code value} is negative (zero is allowed).
    public static int defaultIfNegative(int value, int defaultValue) {
        return value < 0 ? defaultValue : value;
    }

    /// Returns {@code defaultValues} when {@code values} is null or empty.
    public static <T> List<T> defaultListIfEmpty(List<T> values, List<T> defaultValues) {
        return values == null || values.isEmpty() ? defaultValues : List.copyOf(values);
    }

    /// Loads a line-delimited list from a classpath resource.
    /// Lines starting with {@code #} and blank lines are ignored.
    /// Falls back to {@code fallback} silently when the resource is absent or empty.
    public static List<String> loadListFromResource(String resourcePath, List<String> fallback) {
        InputStream stream = ConfigDefaults.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            return List.copyOf(fallback);
        }

        try (stream) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> values = content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

            if (values.isEmpty()) {
                return List.copyOf(fallback);
            }

            return List.copyOf(values);
        } catch (IOException e) {
            return List.copyOf(fallback);
        }
    }
}
