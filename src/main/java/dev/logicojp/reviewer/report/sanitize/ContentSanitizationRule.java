package dev.logicojp.reviewer.report.sanitize;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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