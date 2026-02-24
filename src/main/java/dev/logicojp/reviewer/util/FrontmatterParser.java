package dev.logicojp.reviewer.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// Shared utility for parsing YAML frontmatter from Markdown files.
///
/// Frontmatter is delimited by `---` lines at the start of the file:
/// ```
/// ---
/// key: value
/// nested:
///   sub-key: sub-value
/// ---
/// Body content here...
/// ```
public final class FrontmatterParser {

    static final int MAX_ALIASES_FOR_COLLECTIONS = 10;
    static final int FRONTMATTER_CODEPOINT_LIMIT = 64 * 1024;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
        Pattern.DOTALL
    );

    private static Yaml createYaml() {
        return new Yaml(new SafeConstructor(buildLoaderOptions()));
    }

    private FrontmatterParser() {}

    /// Parsed result of a frontmatter-enabled file.
    public record Parsed(Map<String, String> metadata, String body, boolean hasFrontmatter,
                         String rawFrontmatter) {

        public String getOrDefault(String key, String defaultValue) {
            return metadata.getOrDefault(key, defaultValue);
        }

        public String get(String key) {
            return metadata.get(key);
        }
    }

    /// Parses YAML frontmatter from the given raw content.
    public static Parsed parse(String rawContent) {
        if (!startsWithFrontmatter(rawContent)) {
            return withoutFrontmatter(rawContent);
        }

        Matcher matcher = FRONTMATTER_PATTERN.matcher(rawContent);
        if (!matcher.matches()) {
            return withoutFrontmatter(rawContent);
        }

        String frontmatter = matcher.group(1);
        String body = matcher.group(2);

        Map<String, String> metadata = parseFields(frontmatter);
        return new Parsed(Map.copyOf(metadata), body, true, frontmatter);
    }

    /// Parses a nested block from frontmatter (e.g. `metadata:` section).
    public static Map<String, String> parseNestedBlock(String frontmatterText, String blockKey) {
        Map<String, String> nested = new HashMap<>();
        boolean inBlock = false;

        for (String line : frontmatterText.lines().toList()) {
            String trimmed = line.trim();

            if (trimmed.equals(blockKey + ":")) {
                inBlock = true;
                continue;
            }

            if (!inBlock) continue;

            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) {
                break;
            }

            if (!trimmed.isEmpty()) {
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    String key = trimmed.substring(0, colonIdx).trim();
                    String value = stripQuotes(trimmed.substring(colonIdx + 1).trim());
                    if (!value.isEmpty()) {
                        nested.put(key, value);
                    }
                }
            }
        }

        return nested.isEmpty() ? Map.of() : Map.copyOf(nested);
    }

    /// Extracts the raw frontmatter text (between `---` delimiters) from content.
    static String extractRawFrontmatter(String rawContent) {
        if (!startsWithFrontmatter(rawContent)) {
            return null;
        }
        Matcher matcher = FRONTMATTER_PATTERN.matcher(rawContent);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    /// Parses top-level key-value fields using SnakeYAML.
    /// Falls back to manual parsing for YAML special characters.
    private static Map<String, String> parseFields(String frontmatter) {
        try {
            Map<?, ?> parsed = createYaml().loadAs(frontmatter, Map.class);
            if (parsed == null) {
                return Map.of();
            }
            Map<String, String> fields = new HashMap<>();
            for (var entry : parsed.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    fields.put(key, entry.getValue().toString());
                }
            }
            return fields;
        } catch (org.yaml.snakeyaml.error.YAMLException | ClassCastException _) {
            return parseFieldsManually(frontmatter);
        }
    }

    private static LoaderOptions buildLoaderOptions() {
        var options = new LoaderOptions();
        options.setMaxAliasesForCollections(MAX_ALIASES_FOR_COLLECTIONS);
        options.setCodePointLimit(FRONTMATTER_CODEPOINT_LIMIT);
        return options;
    }

    private static Map<String, String> parseFieldsManually(String frontmatter) {
        Map<String, String> fields = new HashMap<>();

        for (String line : frontmatter.lines().toList()) {
            if (line.isBlank() || line.trim().startsWith("-")
                    || line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0) continue;

            String key = line.substring(0, colonIdx).trim();
            String value = stripQuotes(line.substring(colonIdx + 1).trim());

            if (value.isEmpty()) continue;
            fields.put(key, value);
        }

        return fields;
    }

    static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                 || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean startsWithFrontmatter(String rawContent) {
        return rawContent != null && rawContent.startsWith("---");
    }

    private static Parsed withoutFrontmatter(String rawContent) {
        return new Parsed(Map.of(), rawContent != null ? rawContent : "", false, null);
    }
}
