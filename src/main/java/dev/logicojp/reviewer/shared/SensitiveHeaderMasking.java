package dev.logicojp.reviewer.shared;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Judgement and formatting helpers for sensitive HTTP header values.
///
/// ## This class does not hide anything by itself (ADR-0007 D5)
///
/// It used to hand out `Map` wrappers whose `toString()` masked auth values, so that a header map
/// leaked into a debug log would render as `***`. Those wrappers were deleted. They were a control
/// in appearance only:
///
///   - they guarded `toString()` and nothing else — `get()` and `entrySet()` returned raw values
///     **by design**, so serializers, debuggers and the SDK were never covered;
///   - they were bound to one object instance, so any copy silently dropped the guard;
///   - the class they were built to protect (`copilot-sdk-java`'s `McpHttpServerConfig`) stores the
///     map without a defensive copy and overrides no `toString()` — measured, not assumed — so the
///     one surface they did guard was unreachable through it.
///
/// Masking now happens where output is actually produced: the Logback sink masks every appender by
/// value shape and by header name, independent of which object rendered the text. See
/// `SensitiveHeaderMaskingSinkCanaryTest`.
///
/// What remains here are **pure functions**: they decide whether a header name looks sensitive and
/// format a masked replacement. Callers choose when to apply them. Do not add a wrapper factory
/// back — `LayerDependencyRulesTest` Rule 4b exists to catch the first step of that regression.
public final class SensitiveHeaderMasking {

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

    public static boolean isSensitiveHeaderName(String headerName) {
        String normalized = headerName == null ? "" : headerName.toLowerCase(Locale.ROOT);
        return SENSITIVE_PATTERNS.stream().anyMatch(normalized::contains);
    }

    public static String maskHeaderValue(String headerName, String value) {
        return isSensitiveHeaderName(headerName)
            ? maskSensitiveValue(value)
            : value;
    }

    public static String buildMaskedMapString(Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> maskHeaderValue(entry.getKey(), entry.getValue())
            ))
            .toString();
    }

    public static String maskSensitiveValue(String value) {
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

}
