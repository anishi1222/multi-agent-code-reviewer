package dev.logicojp.reviewer.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Placeholder replacement helpers for `${key}` style templates.
///
/// This syntax is used for both agent prompt construction and report/summary
/// templates. {@link dev.logicojp.reviewer.service.TemplateService} delegates
/// placeholder substitution to this utility.
public final class PlaceholderUtils {

    private static final Pattern DOLLAR_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private PlaceholderUtils() {
        // Utility class
    }

    /// Replaces `${key}` placeholders in one pass.
    /// Unknown placeholders are kept as-is.
    public static String replaceDollarPlaceholders(String template, Map<String, String> values) {
        return DOLLAR_PLACEHOLDER_PATTERN.matcher(template).replaceAll(match -> {
            String key = match.group(1);
            String replacement = values.getOrDefault(key, match.group());
            return Matcher.quoteReplacement(replacement);
        });
    }
}
