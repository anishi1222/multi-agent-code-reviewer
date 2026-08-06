package dev.logicojp.reviewer.domain.report;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A single sanitization rule: a regex pattern and its replacement string.
///
/// Optional fast-check markers allow short-circuiting expensive regex matches
/// when the input does not contain any of the markers.
///
/// Pure {@code java.*} — no framework dependencies.
record ContentSanitizationRule(Pattern pattern, String replacement, List<String> fastCheckMarkers) {

    ContentSanitizationRule(Pattern pattern, String replacement) {
        this(pattern, replacement, List.of());
    }

    String apply(String input) {
        if (!fastCheckMarkers.isEmpty() && !containsAnyMarker(input)) {
            return input;
        }
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = null;
        while (matcher.find()) {
            if (sb == null) {
                sb = new StringBuilder(input.length());
            }
            matcher.appendReplacement(sb, replacement);
        }
        if (sb == null) {
            return input;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean containsAnyMarker(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        for (String marker : fastCheckMarkers) {
            if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
