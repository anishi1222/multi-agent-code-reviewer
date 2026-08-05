package dev.logicojp.reviewer.domain.instruction;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Shared frontmatter parsing helpers for instruction and prompt loaders.
///
/// This domain-layer version uses only {@code java.*} — it does not depend on
/// SnakeYAML or any infrastructure library. A simple regex + line-based parser
/// is sufficient for the frontmatter keys consumed in the domain layer.
/// Full YAML parsing (including nested blocks) is available via
/// {@code util.FrontmatterParser} in the infrastructure layer.
final class InstructionFrontmatter {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
        Pattern.DOTALL
    );

    private InstructionFrontmatter() {
    }

    record Parsed(Map<String, String> metadata, String body, boolean hasFrontmatter) {
        String getOrDefault(String key, String defaultValue) {
            return metadata.getOrDefault(key, defaultValue);
        }

        String get(String key) {
            return metadata.get(key);
        }
    }

    static Parsed parse(String rawContent) {
        if (rawContent == null || !rawContent.startsWith("---")) {
            return new Parsed(Map.of(), rawContent != null ? rawContent : "", false);
        }

        Matcher matcher = FRONTMATTER_PATTERN.matcher(rawContent);
        if (!matcher.matches()) {
            return new Parsed(Map.of(), rawContent, false);
        }

        String frontmatter = matcher.group(1);
        String body = matcher.group(2);
        Map<String, String> metadata = parseFields(frontmatter);
        return new Parsed(Map.copyOf(metadata), body, true);
    }

    static String bodyOrRaw(Parsed parsed, String rawContent) {
        if (!parsed.hasFrontmatter()) {
            return rawContent;
        }
        String body = parsed.body().trim();
        return body.isEmpty() ? rawContent : body;
    }

    private static Map<String, String> parseFields(String frontmatter) {
        Map<String, String> fields = new HashMap<>();
        for (String line : frontmatter.lines().toList()) {
            if (line.isBlank() || line.trim().startsWith("#")
                || line.trim().startsWith("-")
                || line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0) continue;
            String key = line.substring(0, colonIdx).trim();
            String value = stripQuotes(line.substring(colonIdx + 1).trim());
            if (!value.isEmpty()) {
                fields.put(key, value);
            }
        }
        return fields;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
            && ((value.startsWith("'") && value.endsWith("'"))
            || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
