package dev.logicojp.reviewer.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Parses finding blocks and metadata rows from markdown review output.
final class ReviewFindingBlockParser {

    private static final Pattern FINDING_HEADER = Pattern.compile("(?m)^###\\s+(\\d+)\\.\\s+(.+?)\\s*$");
    private static final Pattern TABLE_ROW_TEMPLATE = Pattern.compile(
        "(?m)^\\|\\s*\\*\\*%s\\*\\*\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Map<String, Pattern> TABLE_VALUE_PATTERNS = Map.of(
        "Priority", compileTableRowPattern("Priority"),
        "指摘の概要", compileTableRowPattern("指摘の概要"),
        "該当箇所", compileTableRowPattern("該当箇所")
    );

    private ReviewFindingBlockParser() {
    }

    static List<ReviewResultMerger.FindingBlock> extractFindingBlocks(String content) {
        Matcher matcher = FINDING_HEADER.matcher(content);
        List<int[]> headerPositions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            headerPositions.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(2).trim());
        }
        if (headerPositions.isEmpty()) return List.of();

        List<ReviewResultMerger.FindingBlock> blocks = new ArrayList<>(headerPositions.size());
        for (int i = 0; i < headerPositions.size(); i++) {
            int bodyStart = headerPositions.get(i)[1];
            int bodyEnd = i + 1 < headerPositions.size()
                ? headerPositions.get(i + 1)[0] : content.length();
            String body = content.substring(bodyStart, bodyEnd).trim();
            if (!body.isEmpty()) {
                blocks.add(new ReviewResultMerger.FindingBlock(titles.get(i), body));
            }
        }
        return blocks;
    }

    static String extractTableValue(String body, String key) {
        Pattern pattern = TABLE_VALUE_PATTERNS.getOrDefault(key, compileTableRowPattern(key));
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static Pattern compileTableRowPattern(String key) {
        return Pattern.compile(TABLE_ROW_TEMPLATE.pattern().formatted(Pattern.quote(key)));
    }
}
