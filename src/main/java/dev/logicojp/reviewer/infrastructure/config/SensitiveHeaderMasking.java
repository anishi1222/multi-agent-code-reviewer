package dev.logicojp.reviewer.infrastructure.config;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Utility for creating Map wrappers that mask sensitive header values
/// (e.g. Authorization tokens) in their string representations.
/// Prevents token leakage via SDK/framework debug logging of Map.toString().
final class SensitiveHeaderMasking {

    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
        "authorization", "token", "api-key", "apikey",
        "secret", "password", "credential", "cookie", "x-api-key"
    );

    private SensitiveHeaderMasking() {
    }

    static boolean isSensitiveHeaderName(String headerName) {
        String normalized = headerName == null ? "" : headerName.toLowerCase(Locale.ROOT);
        return SENSITIVE_PATTERNS.stream().anyMatch(normalized::contains);
    }

    static String maskHeaderValue(String headerName, String value) {
        return isSensitiveHeaderName(headerName) ? maskSensitiveValue(value) : value;
    }

    private static String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    static Map<String, String> wrapHeaders(Map<String, String> headers) {
        return new MaskedHeaderMap(headers);
    }

    private static final class MaskedHeaderMap extends java.util.AbstractMap<String, String> {
        private final Map<String, String> delegate;

        MaskedHeaderMap(Map<String, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String get(Object key) {
            return delegate.get(key);
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, String>> iterator() {
                    return delegate.entrySet().stream()
                        .map(e -> (Entry<String, String>) new AbstractMap.SimpleImmutableEntry<>(
                            e.getKey(), maskHeaderValue(e.getKey(), e.getValue())))
                        .iterator();
                }

                @Override
                public int size() {
                    return delegate.size();
                }
            };
        }
    }
}
