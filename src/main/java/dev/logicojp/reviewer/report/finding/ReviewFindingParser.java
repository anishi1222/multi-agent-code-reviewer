package dev.logicojp.reviewer.report.finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewFindingParser {

    private static final Pattern FINDING_HEADER = Pattern.compile("(?m)^###\\s+(\\d+)\\.\\s+(.+?)\\s*$");
    private static final Pattern TRAILING_GLOBAL_SECTION = Pattern.compile(
        "(?im)^##\\s+.+$|^###\\s*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\s*$|^\\*\\*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\*\\*\\s*$"
    );
    private static final Pattern TRAILING_SEPARATOR = Pattern.compile("(?s)(.*?)(?:\\n\\s*---\\s*)+$");
    private static final Pattern OVERALL_HEADER = Pattern.compile(
        "(?im)^(?:##+\\s*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\s*$|\\*\\*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\*\\*\\s*$)"
    );
    private static final Pattern TABLE_ROW_TEMPLATE = Pattern.compile(
        "(?m)^\\|\\s*\\*\\*%s\\*\\*\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Map<String, Pattern> TABLE_VALUE_PATTERNS = Map.of(
        "Priority", compileTablePattern("Priority"),
        "指摘の概要", compileTablePattern("指摘の概要"),
        "該当箇所", compileTablePattern("該当箇所")
    );

    private ReviewFindingParser() {
    }

    public static List<FindingBlock> extractFindingBlocks(String content) {
        Matcher matcher = FINDING_HEADER.matcher(content);
        List<HeaderMatch> headers = new ArrayList<>();
        while (matcher.find()) {
            headers.add(new HeaderMatch(matcher.start(), matcher.end(), matcher.group(2).trim()));
        }

        if (headers.isEmpty()) {
            return List.of();
        }

        List<FindingBlock> blocks = new ArrayList<>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            HeaderMatch current = headers.get(i);
            int bodyEnd = i + 1 < headers.size() ? headers.get(i + 1).startIndex() : content.length();
            String body = normalizeFindingBody(content.substring(current.endIndex(), bodyEnd));
            if (!body.isEmpty()) {
                blocks.add(new FindingBlock(current.title(), body));
            }
        }
        return blocks;
    }

    private static String normalizeFindingBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "";
        }

        int boundaryIndex = findTrailingGlobalSectionStart(rawBody);
        String body = rawBody.substring(0, boundaryIndex).trim();

        return trimTrailingSeparators(body);
    }

    /// Extracts trailing overall-summary text from review content.
    ///
    /// Supported headers include variants such as `## 総評`, `### Summary`, and `**総評**`.
    public static String extractOverallSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        Matcher matcher = OVERALL_HEADER.matcher(content);
        int summaryBodyStart = -1;
        while (matcher.find()) {
            summaryBodyStart = matcher.end();
        }

        if (summaryBodyStart < 0) {
            return "";
        }

        String summary = content.substring(summaryBodyStart).trim();
        return trimTrailingSeparators(summary);
    }

    private static String trimTrailingSeparators(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        Matcher trailingSeparatorMatcher = TRAILING_SEPARATOR.matcher(body);
        if (trailingSeparatorMatcher.matches()) {
            return trailingSeparatorMatcher.group(1).trim();
        }
        return body.trim();
    }

    private static int findTrailingGlobalSectionStart(String rawBody) {
        Matcher matcher = TRAILING_GLOBAL_SECTION.matcher(rawBody);
        return matcher.find() ? matcher.start() : rawBody.length();
    }

    public static String findingKey(FindingBlock block) {
        String priority = extractTableValue(block.body(), "Priority");
        String summary = extractTableValue(block.body(), "指摘の概要");
        String location = extractTableValue(block.body(), "該当箇所");

        String titlePart = ReviewFindingSimilarity.normalizeText(block.title());
        String priorityPart = ReviewFindingSimilarity.normalizeText(priority);
        String summaryPart = ReviewFindingSimilarity.normalizeText(summary);
        String locationPart = ReviewFindingSimilarity.normalizeText(location);

        if (!titlePart.isEmpty() && (!summaryPart.isEmpty() || !locationPart.isEmpty() || !priorityPart.isEmpty())) {
            return String.join("|", titlePart, priorityPart, locationPart, summaryPart);
        }

        return "raw|" + ReviewFindingSimilarity.normalizeText(block.body());
    }

    /// Derives a finding key from an already-computed NormalizedFinding,
    /// avoiding redundant extractTableValue and normalizeText calls.
    public static String findingKeyFromNormalized(AggregatedFinding.NormalizedFinding normalized,
                                                  String rawBody) {
        if (!normalized.title().isEmpty()
            && (!normalized.summary().isEmpty() || !normalized.location().isEmpty()
                || !normalized.priority().isEmpty())) {
            return String.join("|", normalized.title(), normalized.priority(),
                               normalized.location(), normalized.summary());
        }
        return "raw|" + ReviewFindingSimilarity.normalizeText(rawBody);
    }

     static String extractTableValue(String body, String key) {
        Pattern pattern = TABLE_VALUE_PATTERNS.get(key);
        if (pattern == null) {
            pattern = compileTablePattern(key);
        }
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static Pattern compileTablePattern(String key) {
        return Pattern.compile(TABLE_ROW_TEMPLATE.pattern().formatted(Pattern.quote(key)));
    }

    private record HeaderMatch(int startIndex, int endIndex, String title) {
    }

    public record FindingBlock(String title, String body) {
    }
}