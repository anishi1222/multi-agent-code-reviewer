package dev.logicojp.reviewer.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/// Package-private utility for resolving configuration defaults.
/// Mirrors {@code dev.logicojp.reviewer.config.ConfigDefaults} in the new infrastructure layer.
final class ConfigDefaults {

    private static final Logger logger = LoggerFactory.getLogger(ConfigDefaults.class);

    private ConfigDefaults() {
    }

    static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    static int defaultIfNonPositive(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    static long defaultIfNonPositive(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    static double defaultIfNonPositive(double value, double defaultValue) {
        return value <= 0.0 ? defaultValue : value;
    }

    static int defaultIfNegative(int value, int defaultValue) {
        return value < 0 ? defaultValue : value;
    }

    static <T> List<T> defaultListIfEmpty(List<T> values, List<T> defaultValues) {
        return values == null || values.isEmpty() ? defaultValues : List.copyOf(values);
    }

    static List<String> loadListFromResource(String resourcePath, List<String> fallback) {
        InputStream stream = ConfigDefaults.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            logger.debug("Default config resource not found: {}", resourcePath);
            return List.copyOf(fallback);
        }
        try (stream) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> values = content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
            if (values.isEmpty()) {
                logger.debug("Default config resource is empty: {}", resourcePath);
                return List.copyOf(fallback);
            }
            return List.copyOf(values);
        } catch (IOException e) {
            logger.debug("Failed to read default config resource '{}': {}", resourcePath, e.getMessage());
            return List.copyOf(fallback);
        }
    }
}
