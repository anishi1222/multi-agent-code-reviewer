package dev.logicojp.reviewer.config;

import java.util.AbstractMap;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Utility for creating Map wrappers that mask sensitive header values
/// (e.g. Authorization tokens) in their string representations.
/// Prevents token leakage via SDK/framework debug logging of Map.toString().
final class SensitiveHeaderMasking {

    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
        "authorization",
        "token",
        "api-key",
        "apikey",
        "secret",
        "password",
        "credential",
        "cookie",
        "x-api-key"
    );

    private SensitiveHeaderMasking() {
    }

    static boolean isSensitiveHeaderName(String headerName) {
        String normalized = headerName == null ? "" : headerName.toLowerCase(Locale.ROOT);
        return SENSITIVE_PATTERNS.stream().anyMatch(normalized::contains);
    }

    static String maskHeaderValue(String headerName, String value) {
        return isSensitiveHeaderName(headerName)
            ? maskSensitiveValue(value)
            : value;
    }

    static String buildMaskedMapString(Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> maskHeaderValue(entry.getKey(), entry.getValue())
            ))
            .toString();
    }

    static String maskSensitiveValue(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        int spaceIndex = value.indexOf(' ');
        if (spaceIndex > 0) {
            String prefix = value.substring(0, spaceIndex);
            return prefix + " ***";
        }
        return "***";
    }

    /// Map wrapper that delegates toString() to a pre-computed masked string.
    static Map<String, Object> wrapWithMaskedToString(Map<String, Object> delegate, String maskedString) {
        return new MaskedToStringMap(delegate, maskedString);
    }

    /// Creates a header map that masks sensitive values in string representations.
    static Map<String, String> wrapHeaders(Map<String, String> headers) {
        return new MaskedHeadersMap(headers);
    }

    private static final class MaskedToStringMap extends AbstractMap<String, Object> {
        private final Map<String, Object> delegate;
        private final String maskedString;

        MaskedToStringMap(Map<String, Object> delegate, String maskedString) {
            this.delegate = Map.copyOf(delegate);
            this.maskedString = maskedString;
        }

        @Override public Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
        @Override public Object get(Object key) { return delegate.get(key); }
        @Override public int size() { return delegate.size(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
        @Override public boolean containsValue(Object value) { return delegate.containsValue(value); }
        @Override public Set<String> keySet() { return delegate.keySet(); }
        @Override public Collection<Object> values() { return delegate.values(); }
        @Override public String toString() { return maskedString; }
    }

    private static final class MaskedHeadersMap extends AbstractMap<String, String> {
        private final Map<String, String> delegate;

        MaskedHeadersMap(Map<String, String> delegate) {
            this.delegate = Map.copyOf(delegate);
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    Iterator<Map.Entry<String, String>> iterator = delegate.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, String> next() {
                            return new MaskedHeaderEntry(iterator.next());
                        }
                    };
                }

                @Override
                public int size() {
                    return delegate.size();
                }

                @Override
                public String toString() {
                    return buildMaskedMapString(delegate);
                }
            };
        }

        /// Returns the raw (unmasked) value so downstream SDK calls can use real credentials.
        /// Logging safety is provided by toString()/entry rendering and Logback masking.
        @Override
        public String get(Object key) {
            return delegate.get(key);
        }

        @Override
        public Set<String> keySet() {
            return delegate.keySet();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return delegate.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return delegate.containsValue(value);
        }

        @Override
        public Collection<String> values() {
            var values = delegate.values();
            return new AbstractCollection<>() {
                @Override
                public int size() {
                    return values.size();
                }

                @Override
                public Iterator<String> iterator() {
                    Iterator<Map.Entry<String, String>> entryIterator = delegate.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return entryIterator.hasNext();
                        }

                        @Override
                        public String next() {
                            Map.Entry<String, String> entry = entryIterator.next();
                            return maskHeaderValue(entry.getKey(), entry.getValue());
                        }
                    };
                }

                @Override
                public String toString() {
                    List<String> maskedValues = new ArrayList<>(delegate.size());
                    for (Entry<String, String> entry : delegate.entrySet()) {
                        maskedValues.add(maskHeaderValue(entry.getKey(), entry.getValue()));
                    }
                    return maskedValues.toString();
                }
            };
        }

        @Override
        public String toString() {
            return buildMaskedMapString(delegate);
        }
    }

    private static final class MaskedHeaderEntry implements Map.Entry<String, String> {
        private final Map.Entry<String, String> delegate;

        MaskedHeaderEntry(Map.Entry<String, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getKey() {
            return delegate.getKey();
        }

        @Override
        public String getValue() {
            return delegate.getValue();
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException("MaskedHeadersMap entries are immutable");
        }

        @Override
        public String toString() {
            return getKey() + "=" + maskHeaderValue(getKey(), getValue());
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof Map.Entry<?, ?> entry)) {
                return false;
            }
            return Objects.equals(getKey(), entry.getKey())
                && Objects.equals(getValue(), entry.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }
    }
}
