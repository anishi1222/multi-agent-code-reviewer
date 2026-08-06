package dev.logicojp.reviewer.infrastructure.parsing;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Parses YAML-like frontmatter from Markdown content.
public final class FrontmatterParser {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?",
        Pattern.DOTALL
    );
    private static final Pattern KV_PATTERN = Pattern.compile(
        "^\\s*([\\w.-]+)\\s*:\\s*(.*)$"
    );

    private FrontmatterParser() {
    }

    public static FrontmatterResult parse(String content) {
        if (content == null || content.isBlank()) {
            return new FrontmatterResult(Map.of(), content == null ? "" : content, false, null);
        }
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new FrontmatterResult(Map.of(), content, false, null);
        }
        String frontmatterBlock = matcher.group(1);
        String body = content.substring(matcher.end());
        Map<String, String> fields = parseFields(frontmatterBlock);
        return new FrontmatterResult(fields, body.strip(), true, frontmatterBlock);
    }

    /// Parses a nested block from frontmatter (e.g. `metadata:` section).
    public static Map<String, String> parseNestedBlock(String frontmatterText, String blockKey) {
        Map<String, String> nested = new HashMap<>();
        boolean inBlock = false;
        for (String line : frontmatterText.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.equals(blockKey + ":")) {
                inBlock = true;
                continue;
            }
            if (!inBlock) continue;
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) break;
            if (trimmed.isEmpty()) continue;
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0) continue;
            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isEmpty()) nested.put(key, value);
        }
        return nested.isEmpty() ? Map.of() : Map.copyOf(nested);
    }

    private static Map<String, String> parseFields(String block) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : block.split("\\r?\\n")) {
            Matcher kv = KV_PATTERN.matcher(line);
            if (kv.matches()) {
                String key = kv.group(1).trim();
                String value = kv.group(2).trim();
                // Strip surrounding quotes if present
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        return Map.copyOf(result);
    }

    public record FrontmatterResult(Map<String, String> fields, String body, boolean hasFrontmatter,
                                    String rawFrontmatter) {
        public String field(String key) {
            return fields.get(key);
        }

        public String fieldOrDefault(String key, String defaultValue) {
            return fields.getOrDefault(key, defaultValue);
        }
    }
}
