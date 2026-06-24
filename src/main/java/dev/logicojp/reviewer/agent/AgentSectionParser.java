package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AgentSectionParser {

    private static final Logger logger = LoggerFactory.getLogger(AgentSectionParser.class);

    private static final Pattern FOCUS_AREA_PATTERN = Pattern.compile(
        "##\\s*Focus Areas\\s*\\n((?:\\s*[-*]\\s*.+\\n?)+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile("^##\\s+(.+)$");
    private static final Set<String> RECOGNIZED_SECTIONS = Set.of(
        "role",
        "instruction",
        "output format",
        "focus areas"
    );
    private static final String DEFAULT_FOCUS_AREA = "一般的なコード品質";

    private final String defaultOutputFormat;

    AgentSectionParser(String defaultOutputFormat) {
        this.defaultOutputFormat = defaultOutputFormat;
    }

    String systemPrompt(Map<String, String> sections, String body) {
        String systemPrompt = getSection(sections, "role");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return body.trim();
        }
        return systemPrompt;
    }

    String instruction(Map<String, String> sections) {
        return getSection(sections, "instruction");
    }

    String outputFormat(Map<String, String> sections) {
        String outputFormat = getSection(sections, "output format");
        if ((outputFormat == null || outputFormat.isBlank()) && defaultOutputFormat != null) {
            return defaultOutputFormat;
        }
        return outputFormat;
    }

    List<String> focusAreas(Map<String, String> sections, String body) {
        String focusAreasSection = getSection(sections, "focus areas");
        return focusAreasSection != null
            ? parseFocusAreaItems(focusAreasSection)
            : extractFocusAreas(body);
    }

    Map<String, String> extractSections(String body) {
        Map<String, StringBuilder> sectionBuilders = new LinkedHashMap<>();
        String currentKey = null;

        for (String line : body.lines().toList()) {
            String sectionKey = extractRecognizedSectionKey(line);
            if (sectionKey != null) {
                if (sectionBuilders.containsKey(sectionKey)) {
                    logger.warn("Duplicate section '## {}' found; using the last occurrence.", sectionKey);
                }
                currentKey = sectionKey;
                sectionBuilders.put(currentKey, new StringBuilder());
                continue;
            }

            if (currentKey != null) {
                sectionBuilders.get(currentKey).append(line).append("\n");
            }
        }

        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : sectionBuilders.entrySet()) {
            sections.put(entry.getKey(), entry.getValue().toString().trim());
        }
        return sections;
    }

    private List<String> extractFocusAreas(String body) {
        List<String> focusAreas = new ArrayList<>();

        Matcher matcher = FOCUS_AREA_PATTERN.matcher(body);
        if (matcher.find()) {
            String listContent = matcher.group(1);
            focusAreas = new ArrayList<>(parseBulletItems(listContent));
        }

        if (focusAreas.isEmpty()) {
            focusAreas.add(DEFAULT_FOCUS_AREA);
        }

        return List.copyOf(focusAreas);
    }

    private List<String> parseFocusAreaItems(String sectionContent) {
        List<String> focusAreas = new ArrayList<>(parseBulletItems(sectionContent));
        if (focusAreas.isEmpty()) {
            logger.warn("Focus Areas section found but contains no bullet items; using default.");
            focusAreas.add(DEFAULT_FOCUS_AREA);
        }
        return List.copyOf(focusAreas);
    }

    private List<String> parseBulletItems(String text) {
        return text.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("-") || line.startsWith("*"))
            .map(line -> line.substring(1).trim())
            .filter(item -> !item.isEmpty())
            .toList();
    }

    private String extractRecognizedSectionKey(String line) {
        Matcher matcher = SECTION_HEADER_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return null;
        }
        String sectionKey = normalizeSectionKey(matcher.group(1));
        return RECOGNIZED_SECTIONS.contains(sectionKey) ? sectionKey : null;
    }

    private String getSection(Map<String, String> sections, String... keys) {
        for (String key : keys) {
            String normalized = normalizeSectionKey(key);
            if (sections.containsKey(normalized)) {
                return sections.get(normalized);
            }
        }
        return null;
    }

    private String normalizeSectionKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    static String extractNameFromFilename(String filename) {
        String name = filename;
        if (name.endsWith(".agent.md")) {
            name = name.substring(0, name.length() - ".agent.md".length());
        } else if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - ".md".length());
        }
        return name;
    }
}
