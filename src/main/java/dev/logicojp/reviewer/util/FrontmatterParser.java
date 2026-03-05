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
///
/// This parser extracts top-level key-value pairs and nested blocks (one level deep).
/// Used by agent definition parsers, skill parsers, instruction loaders, and prompt loaders.
public final class FrontmatterParser {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
        Pattern.DOTALL
    );

    private FrontmatterParser() {}

    /// Parsed result of a frontmatter-enabled file.
    ///
    /// @param metadata        Top-level key-value pairs from the frontmatter (quotes stripped)
    /// @param body            The content after the closing `---` delimiter
    /// @param hasFrontmatter  Whether the front-matter delimiters were actually present
    /// @param rawFrontmatter  The raw frontmatter text between delimiters (null if no frontmatter)
    public record Parsed(Map<String, String> metadata, String body, boolean hasFrontmatter,
                         String rawFrontmatter) {

        /// Convenience accessor with a default value.
        public String getOrDefault(String key, String defaultValue) {
            return metadata.getOrDefault(key, defaultValue);
        }

        /// Returns the value for the given key, or {@code null} if absent.
        public String get(String key) {
            return metadata.get(key);
        }
    }

    /// Parses YAML frontmatter from the given raw content.
    ///
    /// If no valid frontmatter delimiters are found, returns an empty metadata map
    /// and the entire content as the body.
    ///
    /// @param rawContent The raw file content
    /// @return A {@link Parsed} record with separated metadata and body
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
    ///
    /// @param frontmatterText The raw frontmatter text (between `---` delimiters)
    /// @param blockKey        The key that starts the nested block (e.g. "metadata")
    /// @return Key-value pairs from the nested block, or empty map if not found
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

            // End of block: non-indented, non-empty line
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) {
                break;
            }

            parseBlockEntry(trimmed, nested);
        }

        return nested.isEmpty() ? Map.of() : Map.copyOf(nested);
    }

    /// Parses a single indented `key: value` entry and adds it to the map.
    private static void parseBlockEntry(String trimmed, Map<String, String> nested) {
        if (trimmed.isEmpty()) return;

        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) return;

        String key = trimmed.substring(0, colonIdx).trim();
        String value = stripQuotes(trimmed.substring(colonIdx + 1).trim());
        if (!value.isEmpty()) {
            nested.put(key, value);
        }
    }

    /// Extracts the raw frontmatter text (between `---` delimiters) from content.
    /// Useful when callers need to perform additional custom parsing on the raw frontmatter.
    ///
    /// @param rawContent The raw file content
    /// @return The raw frontmatter text, or {@code null} if no frontmatter is found
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

    /// Parses top-level key-value fields from frontmatter text using SnakeYAML.
    /// Falls back to manual line-based parsing when the frontmatter contains
    /// YAML special characters (e.g. unquoted glob patterns like {@code **/*.py}).
    private static Map<String, String> parseFields(String frontmatter) {
        try {
            var loaderOptions = new LoaderOptions();
            loaderOptions.setCodePointLimit(64 * 1024);
            var yaml = new Yaml(new SafeConstructor(loaderOptions));
            Map<?, ?> parsed = yaml.loadAs(frontmatter, Map.class);
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

    /// Manual line-based fallback for frontmatter that SnakeYAML cannot parse.
    private static Map<String, String> parseFieldsManually(String frontmatter) {
        Map<String, String> fields = new HashMap<>();
        for (String line : frontmatter.lines().toList()) {
            if (isNestedOrIgnoredLine(line)) continue;
            parseTopLevelField(line, fields);
        }
        return fields;
    }

    /// Returns true for lines that are not top-level key-value entries.
    private static boolean isNestedOrIgnoredLine(String line) {
        return line.isBlank()
            || line.trim().startsWith("-")
            || line.startsWith(" ")
            || line.startsWith("\t");
    }

    /// Parses a top-level `key: value` entry and adds it to the map.
    private static void parseTopLevelField(String line, Map<String, String> fields) {
        int colonIdx = line.indexOf(':');
        if (colonIdx <= 0) return;
        String key = line.substring(0, colonIdx).trim();
        String value = stripQuotes(line.substring(colonIdx + 1).trim());
        if (!value.isEmpty()) {
            fields.put(key, value);
        }
    }

    /// Strips surrounding single or double quotes from a value string.
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
